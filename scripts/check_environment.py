import os, shutil
required=['java','cmake','ninja']
android=['adb','sdkmanager']
print('Core tools:')
for x in required+android: print(f'  {x}: {shutil.which(x) or "MISSING"}')
print('ANDROID_HOME:', os.environ.get('ANDROID_HOME','MISSING'))
print('ANDROID_SDK_ROOT:', os.environ.get('ANDROID_SDK_ROOT','MISSING'))
