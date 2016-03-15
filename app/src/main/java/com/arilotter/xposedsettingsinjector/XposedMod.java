package com.arilotter.xposedsettingsinjector;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.newInstance;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

public class XposedMod implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    @SuppressWarnings("unused")
    private static final String TAG = "InjectXposedPreference";
    private XSharedPreferences mPrefs = new XSharedPreferences(XposedMod.this.getClass().getPackage().getName());

    private Class<?> DashboardCategory;
    private Class<?> DashboardTile;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        ModuleLoader.copyModulesListToSd();
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("com.android.settings")) {

            Class<?> SettingsActivity = findClass("com.android.settings.SettingsActivity", lpparam.classLoader);
            Class<?> DashboardSummary = findClass("com.android.settings.dashboard.DashboardSummary", lpparam.classLoader);
            DashboardCategory = findClass("com.android.settings.dashboard.DashboardCategory", lpparam.classLoader);
            DashboardTile = findClass("com.android.settings.dashboard.DashboardTile", lpparam.classLoader);

            try {
                findAndHookMethod(DashboardSummary, "updateTileView", Context.class, Resources.class, DashboardTile, ImageView.class, TextView.class, TextView.class, updateTileViewHook);
            } catch (Throwable t) {
                try { // CyanogenMod takes an additional Switch parameter
                    findAndHookMethod(DashboardSummary, "updateTileView", Context.class, Resources.class, DashboardTile, ImageView.class, TextView.class, TextView.class, Switch.class, updateTileViewHook);
                } catch (Throwable t2) {
                    XposedBridge.log("[XposedSettingsInjector] Error hooking updateTileView in settings");
                    XposedBridge.log(t2);
                }
            }

            try {
                if (Build.VERSION.SDK_INT >= 23)
                    findAndHookMethod(SettingsActivity, "loadCategoriesFromResource", int.class, List.class, Context.class, loadCategoriesFromResourceHook);
                else
                    findAndHookMethod(SettingsActivity, "loadCategoriesFromResource", int.class, List.class, loadCategoriesFromResourceHook);
            } catch (Throwable t) {
                XposedBridge.log("[XposedSettingsInjector] Error hooking loadCategoriesFromResource in settings");
                XposedBridge.log(t);
            }

        } else if (lpparam.packageName.equals("de.robv.android.xposed.installer")) {

            try {
                findAndHookMethod("de.robv.android.xposed.installer.util.ModuleUtil", lpparam.classLoader, "updateModulesList", boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        ModuleLoader.copyModulesListToSd();
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("[XposedSettingsInjector] Error hooking updateModulesList in installer");
                XposedBridge.log(t);
            }

        }
    }

    private XC_MethodHook loadCategoriesFromResourceHook = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            mPrefs.reload();
            boolean onlyInstaller = mPrefs.getBoolean("only_installer", false);
            Context context;
            if (Build.VERSION.SDK_INT >= 23)
                context = (Context) param.args[2];
            else
                context = (Context) param.thisObject; // Surrounding activity
            PackageManager pm = context.getPackageManager();
            ArrayList<ApplicationInfo> xposedModulesList = new ArrayList<>();
            List<String> activeModules = ModuleLoader.getActiveModulesFromSd();
            ApplicationInfo installerInfo = null;

            for (PackageInfo pkg : context.getPackageManager().getInstalledPackages(PackageManager.GET_META_DATA)) {
                ApplicationInfo app = pkg.applicationInfo;
                boolean isXposedInstaller = app.packageName.equals("de.robv.android.xposed.installer");

                if ((!activeModules.contains(app.sourceDir) || !app.enabled) && !isXposedInstaller)
                    continue;

                if (!onlyInstaller && app.metaData != null && app.metaData.containsKey("xposedmodule")) {
                    xposedModulesList.add(app);
                } else if (isXposedInstaller) {
                    installerInfo = app;
                }
            }

            if (!onlyInstaller)
                Collections.sort(xposedModulesList, new ApplicationInfo.DisplayNameComparator(pm));
            if (installerInfo != null)
                xposedModulesList.add(0, installerInfo); // Always add the Xposed installer as the first item

            Object category = DashboardCategory.newInstance();
            setObjectField(category, "title", "Xposed");
            boolean showDescriptions = mPrefs.getBoolean("show_descriptions", true);

            for (ApplicationInfo info : xposedModulesList) {
                Intent intent = ModuleLoader.getSettingsIntent(pm, info.packageName);
                if (intent != null) {
                    Object tile = newInstance(DashboardTile);
                    XposedHelpers.setAdditionalInstanceField(tile, "xposed_item", true);
                    setObjectField(tile, "title", pm.getApplicationLabel(info));
                    if (showDescriptions) {
                        if (info.metaData != null && info.metaData.containsKey("xposeddescription"))
                            setObjectField(tile, "summary", info.metaData.getString("xposeddescription"));
                    }
                    if (Build.VERSION.SDK_INT >= 23) {
                        setIntField(tile, "iconRes", info.icon);
                        setObjectField(tile, "iconPkg", info.packageName);
                    } else {
                        XposedHelpers.setAdditionalInstanceField(tile, "icon", pm.getApplicationIcon(info));
                    }
                    setObjectField(tile, "intent", intent);
                    callMethod(category, "addTile", tile);
                }
            }
            // Add our category to the list of categories
            //noinspection unchecked
            ((List) param.args[1]).add(category);
        }
    };

    private XC_MethodHook updateTileViewHook = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
            setIconAndTint(param);
        }
    };

    private void setIconAndTint(XC_MethodHook.MethodHookParam param) {
        ImageView view = ((ImageView) param.args[3]);
        if (Build.VERSION.SDK_INT >= 23) {
            Object bool = XposedHelpers.getAdditionalInstanceField(param.args[2], "xposed_item");
            if (bool != null && (boolean) bool) {
                Drawable icon = view.getDrawable();
                icon.setTintList(null);
            }
        } else {
            Object icon = XposedHelpers.getAdditionalInstanceField(param.args[2], "icon");
            if (icon != null) {
                view.setImageDrawable((Drawable) icon);
            }
        }
    }

}