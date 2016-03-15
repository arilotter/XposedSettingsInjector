package com.arilotter.xposedsettingsinjector;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

public class ModuleLoader {

    @SuppressLint("SdCardPath")
    private static final String MODULES_LIST_FILE = "/data/data/de.robv.android.xposed.installer/conf/modules.list";
    private static final String PUBLIC_MODULES_LIST_FILE = "/storage/emulated/0/XposedSettingsInjector/modules.list";

    private static final String SETTINGS_CATEGORY = "de.robv.android.xposed.category.MODULE_SETTINGS";

    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void copyModulesListToSd() throws IOException {
        File src = new File(MODULES_LIST_FILE);
        File dst = new File(PUBLIC_MODULES_LIST_FILE);
        if (!src.exists()) {
            XposedBridge.log("[XposedSettingsInjector] No modules.list found!");
            return;
        }
        if (!dst.exists()) {
            dst.getParentFile().mkdirs();
            dst.createNewFile();
            dst.setReadable(true, false);
            dst.setWritable(true, false);
        }
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    /* Taken from Xposed Installer */
    static Intent getSettingsIntent(PackageManager pm, String packageName) {
        // taken from ApplicationPackageManager.getLaunchIntentForPackage(String)
        // first looks for an Xposed-specific category, falls back to getLaunchIntentForPackage

        Intent intentToResolve = new Intent(Intent.ACTION_MAIN);
        intentToResolve.addCategory(SETTINGS_CATEGORY);
        intentToResolve.setPackage(packageName);
        List<ResolveInfo> ris = pm.queryIntentActivities(intentToResolve, 0);

        if (ris == null || ris.size() <= 0) {
            return pm.getLaunchIntentForPackage(packageName);
        }

        Intent intent = new Intent(intentToResolve);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(ris.get(0).activityInfo.packageName, ris.get(0).activityInfo.name);
        return intent;
    }

    static List<String> getActiveModulesFromSd() throws IOException {
        List<String> modules = new ArrayList<>();
        File publicFile = new File(PUBLIC_MODULES_LIST_FILE);
        if (!publicFile.exists()) {
            XposedBridge.log("[XposedSettingsInjector] No public copy of modules.list found!");
            return modules;
        }
        String line;
        BufferedReader br = new BufferedReader(new FileReader(publicFile));
        while ((line = br.readLine()) != null) {
            modules.add(line);
        }
        br.close();
        return modules;
    }

}