import os
import sys
import subprocess
import zipfile
import shutil

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
ANDROID_SDK = r"C:\Users\user\AppData\Local\Android\Sdk"
BUILD_TOOLS = os.path.join(ANDROID_SDK, "build-tools", "35.0.0")
ANDROID_JAR = os.path.join(ANDROID_SDK, "platforms", "android-35", "android.jar")
D8_BAT = os.path.join(BUILD_TOOLS, "d8.bat")
ZIPALIGN_EXE = os.path.join(BUILD_TOOLS, "zipalign.exe")
APKSIGNER_BAT = os.path.join(BUILD_TOOLS, "apksigner.bat")

CLASSES_DIR = os.path.join(ROOT_DIR, "dm", "android", "app", "build", "classes")
DEX_DIR = os.path.join(ROOT_DIR, "dm", "android", "app", "build", "dex")
JAVA_SRC_DIR = os.path.join(ROOT_DIR, "dm", "android", "app", "java")
SO_PATH = os.path.join(ROOT_DIR, "dm", "android", "out", "host-01008DB008C2C000-vulkan", "lib", "libnxnative.so")
KEYSTORE = os.path.join(ROOT_DIR, "debug.keystore")

def run(cmd, cwd=ROOT_DIR):
    print(f"[RUN] {cmd}")
    res = subprocess.run(cmd, cwd=cwd, shell=True, text=True, capture_output=True)
    if res.returncode != 0:
        print(f"[STDERR]\n{res.stderr}")
        print(f"[STDOUT]\n{res.stdout}")
        sys.exit(res.returncode)
    if res.stdout:
        print(res.stdout.strip())

def main():
    print("=== Step 1: Compiling Java Sources ===")
    os.makedirs(CLASSES_DIR, exist_ok=True)
    os.makedirs(DEX_DIR, exist_ok=True)

    java_files = []
    for root, _, files in os.walk(JAVA_SRC_DIR):
        for f in files:
            if f.endswith(".java"):
                java_files.append(os.path.join(root, f))

    sources_list_file = os.path.join(CLASSES_DIR, "sources.txt")
    with open(sources_list_file, "w", encoding="utf-8") as f:
        for jf in java_files:
            f.write(jf.replace("\\", "/") + "\n")

    run(f'javac -encoding UTF-8 -cp "{ANDROID_JAR}" -d "{CLASSES_DIR}" @"{sources_list_file}"')
    print("Java compilation complete.")

    print("\n=== Step 2: Generating classes.dex ===")
    class_files = []
    for root, _, files in os.walk(CLASSES_DIR):
        for f in files:
            if f.endswith(".class"):
                class_files.append(os.path.join(root, f))

    classes_list_file = os.path.join(DEX_DIR, "classes.txt")
    with open(classes_list_file, "w") as f:
        for cf in class_files:
            f.write(cf.replace("\\", "/") + "\n")

    run(f'"{D8_BAT}" --lib "{ANDROID_JAR}" --output "{DEX_DIR}" ' + " ".join([f'"{c}"' for c in class_files]))
    dex_path = os.path.join(DEX_DIR, "classes.dex")
    if not os.path.exists(dex_path):
        print("ERROR: classes.dex not found!")
        sys.exit(1)
    print("classes.dex built successfully.")

    print("\n=== Step 2.5: Generating AndroidManifest.xml (Version 1.0.0) ===")
    AAPT2_EXE = os.path.join(BUILD_TOOLS, "aapt2.exe")
    build_dir = os.path.join(ROOT_DIR, "dm", "android", "app", "build")
    res_dir = os.path.join(build_dir, "res")
    os.makedirs(res_dir, exist_ok=True)
    base_apk = os.path.join(ROOT_DIR, "GalarNative-Optimized.apk")

    with zipfile.ZipFile(base_apk, "r") as z:
        for name in z.namelist():
            if name.startswith("res/"):
                z.extract(name, build_dir)

    manifest_xml = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.nxnative.pokemonshield" android:versionCode="100" android:versionName="1.0.0">
    <uses-sdk android:minSdkVersion="31" android:targetSdkVersion="35" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <uses-feature android:name="android.hardware.vulkan.version" android:version="0x401000" android:required="true" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <uses-feature android:name="android.hardware.gamepad" android:required="false" />
    <application android:label="Galar Native" android:icon="@mipmap/ic_launcher" android:hasCode="true" android:extractNativeLibs="true"
                 android:allowNativeHeapPointerTagging="false" android:isGame="true" android:appCategory="game" android:largeHeap="true" android:hasFragileUserData="true">
        <activity android:name="com.nxnative.SetupActivity" android:exported="true" android:theme="@android:style/Theme.DeviceDefault"
                  android:configChanges="orientation|screenSize|screenLayout|keyboard|keyboardHidden|navigation|uiMode|smallestScreenSize|density">
            <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
        </activity>
        <activity android:name="com.nxnative.GameActivity" android:exported="false" android:screenOrientation="sensorLandscape" android:launchMode="singleTask" android:hardwareAccelerated="false"
                  android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen"
                  android:configChanges="orientation|screenSize|screenLayout|keyboard|keyboardHidden|navigation|uiMode|smallestScreenSize|density">
            <meta-data android:name="android.app.lib_name" android:value="nxnative" />
        </activity>
    </application>
</manifest>'''

    m_path = os.path.join(build_dir, "AndroidManifest.xml")
    with open(m_path, "w", encoding="utf-8") as f:
        f.write(manifest_xml)

    res_zip = os.path.join(build_dir, "res.zip")
    run(f'"{AAPT2_EXE}" compile --dir "{res_dir}" -o "{res_zip}"')
    base_linked_apk = os.path.join(build_dir, "base_linked.apk")
    run(f'"{AAPT2_EXE}" link -o "{base_linked_apk}" -I "{ANDROID_JAR}" --manifest "{m_path}" "{res_zip}"')

    with zipfile.ZipFile(base_linked_apk, "r") as zlink:
        new_manifest_bytes = zlink.read("AndroidManifest.xml")
        new_resources_arsc = zlink.read("resources.arsc")

    print("\n=== Step 3: Updating APK Archive ===")
    temp_apk = os.path.join(ROOT_DIR, "temp_repack.apk")
    aligned_apk = os.path.join(ROOT_DIR, "GalarNative-aligned.apk")
    final_apk = os.path.join(ROOT_DIR, "GalarNative-Optimized.apk")

    # Read base apk and write to temp apk with updated files
    with zipfile.ZipFile(base_apk, "r") as zin:
        with zipfile.ZipFile(temp_apk, "w", compression=zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                if item.filename in ["classes.dex", "lib/arm64-v8a/libnxnative.so", "AndroidManifest.xml", "resources.arsc"] or item.filename.startswith("META-INF/"):
                    continue
                zout.writestr(item, zin.read(item.filename))
            print("Writing new AndroidManifest.xml (Version 1.0.0)...")
            zout.writestr("AndroidManifest.xml", new_manifest_bytes)
            print("Writing new resources.arsc...")
            zout.writestr("resources.arsc", new_resources_arsc)
            print("Writing new classes.dex...")
            zout.write(dex_path, "classes.dex")
            print("Writing new libnxnative.so...")
            zout.write(SO_PATH, "lib/arm64-v8a/libnxnative.so")

    print("\n=== Step 4: Aligning APK ===")
    if os.path.exists(aligned_apk):
        os.remove(aligned_apk)
    run(f'"{ZIPALIGN_EXE}" -p -f 4 "{temp_apk}" "{aligned_apk}"')
    os.remove(temp_apk)

    print("\n=== Step 5: Signing APK ===")
    if os.path.exists(final_apk):
        os.remove(final_apk)
    run(f'"{APKSIGNER_BAT}" sign --ks "{KEYSTORE}" --ks-pass pass:android --key-pass pass:android --out "{final_apk}" "{aligned_apk}"')
    if os.path.exists(aligned_apk):
        os.remove(aligned_apk)

    print("\n=== Step 6: Verifying APK ===")
    run(f'"{APKSIGNER_BAT}" verify --verbose "{final_apk}"')
    print(f"\nSUCCESS! {final_apk} is built, aligned, signed and verified!")

if __name__ == "__main__":
    main()
