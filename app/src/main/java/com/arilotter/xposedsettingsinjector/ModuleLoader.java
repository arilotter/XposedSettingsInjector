package com.arilotter.xposedsettingsinjector;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ModuleLoader {
    @SuppressLint("SdCardPath")
    private static final String MODULES_LIST_FILE = "/data/data/de.robv.android.xposed.installer/conf/modules.list";
    private static final String SETTINGS_CATEGORY = "de.robv.android.xposed.category.MODULE_SETTINGS";

    /* Taken from Xposed Installer */
    static Intent getSettingsIntent(PackageManager pm, String packageName) {
        // taken from
        // ApplicationPackageManager.getLaunchIntentForPackage(String)
        // first looks for an Xposed-specific category, falls back to
        // getLaunchIntentForPackage

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

    static List<String> getActiveModulesWithSu() {
        List<String> modules = new ArrayList<>();
//        try {
//            String line;
//
//            BufferedReader br = new BufferedReader(new FileReader(MODULES_LIST_FILE));
//            while ((line = br.readLine()) != null) {
//                modules.add(line);
//                log("Settings: Found Module: " + line);
//            }
//            br.close();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
        // TODO read this file better
        Process process;
        try {
            String line;
            process = new ProcessBuilder("su", "-c", "cat", MODULES_LIST_FILE).start();

            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((line = in.readLine()) != null) {
                modules.add(line);
            }
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return modules;
    }

    static List<String> getActiveModules() {
        List<String> modules = new ArrayList<>();
        try {
            String line;

            BufferedReader br = new BufferedReader(new FileReader(MODULES_LIST_FILE));
            while ((line = br.readLine()) != null) {
                modules.add(line);
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return modules;
    }
}