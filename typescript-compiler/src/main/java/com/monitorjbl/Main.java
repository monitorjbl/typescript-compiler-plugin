package com.monitorjbl;

import com.eclipsesource.v8.NodeJS;

import java.io.File;

public class Main {

  public static void main(String[] args) throws Exception {
    String tscArgs = System.getProperty("tsc.command");
    tscArgs = tscArgs.substring(1, tscArgs.length() - 1);
    String setArgv = "process.argv = [\"\",\"\"," + tscArgs + "]";
    System.out.println(setArgv);

    NodeJS nodeJS = NodeJS.createNodeJS();
    nodeJS.getRuntime().executeScript(setArgv);
    nodeJS.require(new File(System.getProperty("tsc.buildDir") + "/compiler/typescript/tsc.js"));
  }

}
