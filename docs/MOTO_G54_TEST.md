# MQVOX v4 Moto G54 test protocol

## Text pipeline first
Turn Auto Speak OFF for the first tests so ASR and MT can be diagnosed independently.

Arabic -> English samples:
1. `تجربة واحد اثنان ثلاثة`
2. `مرحبا، كيف حالك اليوم؟`
3. `أريد الذهاب إلى المطار غداً في الساعة الثامنة.`
4. `كم سعر هذه الحقيبة؟`

English -> Arabic samples:
1. `Testing one two three.`
2. `Hello, how are you today?`
3. `I want to go to the airport tomorrow at eight o'clock.`
4. `How much is this bag?`

For each test record:
- recognized text exactly as displayed;
- ASR seconds;
- translated text exactly as displayed;
- MT seconds;
- total seconds;
- whether meaning/numbers/names were preserved.

## Voice test
After the text pipeline is acceptable, turn Auto Speak ON and record TTS latency and intelligibility.

## Offline acceptance
After a successful connected installation, enable airplane mode and separately confirm Wi-Fi is OFF. Restart MQVOX and repeat both directions. Translation must not require a network connection.
