package com.zaze.server;

import com.zaze.accessibility.AdRulesLoader;
import com.zaze.server.vo.AdRules;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class Main {
    public static void main(String[] args) {
        try {
            AdRules adRules = AdRulesLoader.INSTANCE.parseLTTRules(new FileInputStream(new File("data/ltt_rules.json")));
            System.out.println("Hello world! : " + adRules);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}