from pathlib import Path
import re, sys, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
manifest_path=root/'app/src/main/AndroidManifest.xml'
tree=ET.parse(manifest_path); android='{http://schemas.android.com/apk/res/android}'
permissions=[e.attrib.get(android+'name','') for e in tree.getroot().findall('uses-permission')]
errors=[]
if 'android.permission.INTERNET' in permissions: errors.append('INTERNET permission present')
if any(p not in {'android.permission.RECORD_AUDIO'} for p in permissions): errors.append('Unexpected permission(s): '+str(permissions))
text_files=[p for p in root.rglob('*') if p.is_file() and p.suffix.lower() in {'.kt','.kts','.xml','.gradle','.properties'}]
blob='\n'.join(p.read_text(encoding='utf-8',errors='ignore') for p in text_files)
for token in ['com.google.firebase','firebase-analytics','crashlytics','retrofit2','okhttp3']:
    if token.lower() in blob.lower(): errors.append('Forbidden/unnecessary network or telemetry dependency token: '+token)
if errors: print('\n'.join(errors)); sys.exit(1)
print('PASS: permissions =',permissions,'; no cloud/analytics/network client dependency tokens detected.')
