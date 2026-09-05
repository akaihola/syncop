# Development shell: JDK, Gradle and the Android SDK needed to build Syncop.
# Usage: NIXPKGS_ALLOW_UNFREE=1 nix-shell
{ pkgs ? import <nixpkgs> {
    config = {
      allowUnfree = true;
      android_sdk.accept_license = true;
    };
  }
}:
let
  android = pkgs.androidenv.composeAndroidPackages {
    cmdLineToolsVersion = "latest";
    platformToolsVersion = "latest";
    buildToolsVersions = [ "34.0.0" "35.0.0" ];
    platformVersions = [ "35" ];
    includeEmulator = false;
    includeSources = false;
    includeSystemImages = false;
  };
in
pkgs.mkShell {
  packages = [ pkgs.jdk17 pkgs.gradle android.androidsdk ];
  ANDROID_HOME = "${android.androidsdk}/libexec/android-sdk";
  ANDROID_SDK_ROOT = "${android.androidsdk}/libexec/android-sdk";
  JAVA_HOME = pkgs.jdk17.home;
  GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${android.androidsdk}/libexec/android-sdk/build-tools/35.0.0/aapt2";
}
