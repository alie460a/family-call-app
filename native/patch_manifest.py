import re

p = 'android/app/src/main/AndroidManifest.xml'
s = open(p, encoding='utf-8').read()

perms = ['RECORD_AUDIO', 'MODIFY_AUDIO_SETTINGS', 'CAMERA', 'POST_NOTIFICATIONS',
         'FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_SPECIAL_USE', 'RECEIVE_BOOT_COMPLETED',
         'USE_FULL_SCREEN_INTENT', 'WAKE_LOCK']
add = ''.join('    <uses-permission android:name="android.permission.%s" />\n' % x
              for x in perms if ('android.permission.%s"' % x) not in s)

components = '''    <service android:name=".CallService" android:exported="false" android:foregroundServiceType="specialUse">
        <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Checks for incoming family calls" />
    </service>
    <receiver android:name=".BootReceiver" android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.BOOT_COMPLETED" />
        </intent-filter>
    </receiver>
'''

assert '</application>' in s and '</manifest>' in s, 'unexpected manifest'
s = s.replace('</application>', components + '    </application>', 1)
s = s.replace('</manifest>', add + '</manifest>', 1)
# let an incoming call open over the lock screen
s = re.sub(r'(android:name="(?:\.|com\.familycall\.app\.)MainActivity")',
           r'\1 android:showWhenLocked="true" android:turnScreenOn="true"', s, count=1)
open(p, 'w', encoding='utf-8').write(s)
print('manifest patched')
