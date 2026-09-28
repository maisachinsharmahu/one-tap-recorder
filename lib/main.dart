import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const RecorderApp());

class RecorderApp extends StatelessWidget {
  const RecorderApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: 'One Tap',
    theme: ThemeData(
      brightness: Brightness.dark,
      scaffoldBackgroundColor: const Color(0xFF080808),
      colorScheme: const ColorScheme.dark(
        primary: Color(0xFFFF3131),
        surface: Color(0xFF151515),
      ),
      fontFamily: 'sans-serif',
      useMaterial3: true,
    ),
    home: const RecorderHome(),
  );
}

class RecorderHome extends StatefulWidget {
  const RecorderHome({super.key});
  @override
  State<RecorderHome> createState() => _RecorderHomeState();
}

class _RecorderHomeState extends State<RecorderHome>
    with WidgetsBindingObserver {
  static const channel = MethodChannel('one_tap_recorder/control');
  bool recording = false, deviceAudio = true, micAudio = true;
  bool shortcutEnabled = false;
  String quality = '4K';
  int fps = 60;
  Timer? timer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
    timer = Timer.periodic(const Duration(seconds: 1), (_) => _status());
  }

  Future<void> _load() async {
    final data = Map<String, dynamic>.from(
      await channel.invokeMethod('getSettings'),
    );
    quality = data['quality'];
    fps = data['fps'];
    deviceAudio = data['deviceAudio'];
    micAudio = data['micAudio'];
    shortcutEnabled =
        await channel.invokeMethod<bool>('isShortcutEnabled') ?? false;
    await _status();
  }

  Future<void> _status() async {
    final value = await channel.invokeMethod<bool>('isRecording') ?? false;
    if (mounted && value != recording) setState(() => recording = value);
  }

  Future<void> _save() => channel.invokeMethod('saveSettings', {
    'quality': quality,
    'fps': fps,
    'deviceAudio': deviceAudio,
    'micAudio': micAudio,
  });

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _load();
  }

  @override
  void dispose() {
    timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: CustomPaint(
        painter: DotGridPainter(),
        child: ListView(
          padding: const EdgeInsets.fromLTRB(22, 18, 22, 32),
          children: [
            const Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'ONE TAP',
                  style: TextStyle(
                    fontFamily: 'Doto',
                    fontSize: 13,
                    letterSpacing: 4,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                Text(
                  'REC • 01',
                  style: TextStyle(
                    fontFamily: 'Doto',
                    fontSize: 11,
                    color: Colors.white54,
                    letterSpacing: 2,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 44),
            Text(
              recording ? 'RECORDING' : 'SCREEN\nRECORDER',
              style: const TextStyle(
                fontFamily: 'Doto',
                fontSize: 42,
                height: .92,
                letterSpacing: -2,
                fontWeight: FontWeight.w900,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              recording
                  ? 'CAPTURE IN PROGRESS • STATUS BAR ACTIVE'
                  : '4K VIDEO • DEVICE AUDIO • MICROPHONE',
              style: const TextStyle(
                fontSize: 10,
                color: Colors.white54,
                letterSpacing: 1.25,
              ),
            ),
            const SizedBox(height: 42),
            Center(
              child: GestureDetector(
                onTap: () async {
                  await channel.invokeMethod(recording ? 'stop' : 'start');
                  await Future.delayed(const Duration(milliseconds: 400));
                  _status();
                },
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 250),
                  width: 210,
                  height: 210,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: recording ? Colors.white : const Color(0xFFFF3131),
                    boxShadow: [
                      BoxShadow(
                        color:
                            (recording ? Colors.white : const Color(0xFFFF3131))
                                .withValues(alpha: .18),
                        blurRadius: 40,
                      ),
                    ],
                  ),
                  child: Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          recording
                              ? Icons.stop_rounded
                              : Icons.fiber_manual_record,
                          size: 56,
                          color: recording ? Colors.black : Colors.white,
                        ),
                        const SizedBox(height: 7),
                        Text(
                          recording ? 'STOP + SAVE' : 'START',
                          style: TextStyle(
                            fontFamily: 'Doto',
                            color: recording ? Colors.black : Colors.white,
                            fontSize: 13,
                            letterSpacing: 2,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 46),
            _panel('CAPTURE', [
              _segments('RESOLUTION', ['1080P', '4K'], quality, (v) {
                setState(() => quality = v);
                _save();
              }),
              _segments('FRAME RATE', ['30', '60'], '$fps', (v) {
                setState(() => fps = int.parse(v));
                _save();
              }),
            ]),
            const SizedBox(height: 14),
            _panel('AUDIO MIX', [
              _toggle(
                'DEVICE AUDIO',
                'Music, games and supported apps',
                deviceAudio,
                (v) {
                  setState(() => deviceAudio = v);
                  _save();
                },
              ),
              _toggle('MICROPHONE', 'Your voice and surroundings', micAudio, (
                v,
              ) {
                setState(() => micAudio = v);
                _save();
              }),
            ]),
            const SizedBox(height: 14),
            _shortcutCard(),
            const SizedBox(height: 14),
            InkWell(
              onTap: () => channel.invokeMethod('requestTile'),
              borderRadius: BorderRadius.circular(22),
              child: Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: const Color(0xFF151515),
                  borderRadius: BorderRadius.circular(22),
                  border: Border.all(color: Colors.white12),
                ),
                child: const Row(
                  children: [
                    Icon(Icons.widgets_outlined),
                    SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'HOME WIDGET + TILE',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              letterSpacing: 1,
                            ),
                          ),
                          SizedBox(height: 4),
                          Text(
                            'Add the widget from your launcher. Add Screen record from Quick Settings.',
                            style: TextStyle(
                              fontSize: 11,
                              height: 1.4,
                              color: Colors.white54,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Icon(Icons.arrow_outward, size: 18, color: Colors.white54),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 22),
            const Text(
              'Android shows a system capture confirmation before each session. This security screen cannot be removed by third-party apps.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 9, height: 1.5, color: Colors.white38),
            ),
          ],
        ),
      ),
    ),
  );

  Widget _panel(String title, List<Widget> children) => Container(
    decoration: BoxDecoration(
      color: const Color(0xFF151515),
      borderRadius: BorderRadius.circular(22),
      border: Border.all(color: Colors.white12),
    ),
    padding: const EdgeInsets.all(20),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(
            fontFamily: 'Doto',
            fontSize: 10,
            color: Colors.white54,
            letterSpacing: 2,
          ),
        ),
        const SizedBox(height: 14),
        ...children,
      ],
    ),
  );

  Widget _segments(
    String label,
    List<String> options,
    String value,
    ValueChanged<String> change,
  ) => Padding(
    padding: const EdgeInsets.only(bottom: 15),
    child: Row(
      children: [
        Expanded(
          child: Text(
            label,
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
          ),
        ),
        ...options.map(
          (o) => Padding(
            padding: const EdgeInsets.only(left: 6),
            child: ChoiceChip(
              label: Text(o),
              selected: value == o,
              onSelected: (_) => change(o),
              showCheckmark: false,
              selectedColor: Colors.white,
              backgroundColor: Colors.black,
              labelStyle: TextStyle(
                fontSize: 10,
                color: value == o ? Colors.black : Colors.white54,
              ),
              side: BorderSide.none,
            ),
          ),
        ),
      ],
    ),
  );

  Widget _toggle(
    String title,
    String sub,
    bool value,
    ValueChanged<bool> change,
  ) => Padding(
    padding: const EdgeInsets.only(bottom: 10),
    child: Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                sub,
                style: const TextStyle(fontSize: 9, color: Colors.white38),
              ),
            ],
          ),
        ),
        Switch(
          value: value,
          onChanged: change,
          activeThumbColor: const Color(0xFFFF3131),
          activeTrackColor: const Color(0x55FF3131),
        ),
      ],
    ),
  );

  Widget _shortcutCard() => InkWell(
    onTap: () => channel.invokeMethod('openShortcutSettings'),
    borderRadius: BorderRadius.circular(22),
    child: Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFF151515),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: shortcutEnabled ? const Color(0x55FF3131) : Colors.white12,
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: shortcutEnabled ? const Color(0xFFFF3131) : Colors.white10,
            ),
            child: const Icon(Icons.volume_up_rounded, color: Colors.white),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  shortcutEnabled ? 'BUTTON ACTIVE' : 'ENABLE BUTTON SHORTCUT',
                  style: const TextStyle(
                    fontFamily: 'Doto',
                    fontWeight: FontWeight.bold,
                    letterSpacing: 1,
                  ),
                ),
                const SizedBox(height: 5),
                const Text(
                  'Double-press Volume Up to start or stop',
                  style: TextStyle(
                    fontSize: 10,
                    height: 1.35,
                    color: Colors.white54,
                  ),
                ),
              ],
            ),
          ),
          Icon(
            shortcutEnabled ? Icons.check_circle : Icons.arrow_outward,
            size: 20,
            color: shortcutEnabled ? const Color(0xFFFF3131) : Colors.white54,
          ),
        ],
      ),
    ),
  );
}

class DotGridPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = Colors.white.withValues(alpha: .025);
    for (double y = 0; y < size.height; y += 16) {
      for (double x = 0; x < size.width; x += 16) {
        canvas.drawCircle(Offset(x, y), 1, paint);
      }
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
