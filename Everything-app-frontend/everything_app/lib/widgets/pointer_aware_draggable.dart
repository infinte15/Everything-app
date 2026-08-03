import 'dart:async';

import 'package:flutter/gestures.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

/// Ein [Draggable], das seinen Gesten-Erkenner an das Eingabegerät anpasst.
///
/// Warum nicht einfach [LongPressDraggable]: dessen
/// [DelayedMultiDragGestureRecognizer] verwirft die Geste, sobald sich der Zeiger
/// während der Verzögerung weiter als den Hit-Slop bewegt — und der ist für
/// [PointerDeviceKind.mouse] exakt `kPrecisePointerHitSlop`, also **1 Pixel**.
/// Niemand hält eine Maus 350 ms lang auf ein Pixel genau still, deshalb kam mit
/// der Maus praktisch nie ein Drag zustande: gedrückt, minimal bewegt, verworfen.
///
/// Hier stecken deshalb zwei Verhalten in einem Erkenner:
///
///  * **Touch/Stylus** — unverändert Long-Press mit [touchDelay]. Ohne die
///    Verzögerung würde jede Wischgeste, die auf einem Element beginnt, zum Drag
///    und die umgebende Liste ließe sich nicht mehr scrollen.
///  * **Maus** — sofort, sobald [mouseSlop] überschritten ist. Der Slop ist
///    bewusst deutlich größer als das eine Framework-Pixel, damit ein leicht
///    verwackelter Klick weiterhin als Tap durchgeht und nicht als Mini-Drag.
class PointerAwareDraggable<T extends Object> extends Draggable<T> {
  const PointerAwareDraggable({
    super.key,
    required super.child,
    required super.feedback,
    super.data,
    super.axis,
    super.childWhenDragging,
    super.feedbackOffset,
    super.dragAnchorStrategy,
    super.affinity,
    super.maxSimultaneousDrags,
    super.onDragStarted,
    super.onDragUpdate,
    super.onDraggableCanceled,
    super.onDragEnd,
    super.onDragCompleted,
    super.rootOverlay,
    super.hitTestBehavior,
    this.touchDelay = kLongPressTimeout,
    this.mouseSlop = 8.0,
    this.hapticFeedbackOnStart = true,
  });

  /// Haltezeit, bevor ein Finger/Stylus einen Drag auslöst.
  final Duration touchDelay;

  /// Strecke, die die Maus zurücklegen muss, bevor aus dem Klick ein Drag wird.
  final double mouseSlop;

  /// Kurzes haptisches Signal, wenn ein Long-Press-Drag beginnt.
  final bool hapticFeedbackOnStart;

  @override
  MultiDragGestureRecognizer createRecognizer(GestureMultiDragStartCallback onStart) {
    final recognizer = _PointerAwareMultiDragGestureRecognizer(
      delay: touchDelay,
      mouseSlop: mouseSlop,
      debugOwner: this,
    );
    recognizer.onStart = (Offset position) {
      final Drag? drag = onStart(position);
      // Haptik nur da, wo sie etwas aussagt: beim Long-Press ist sie die einzige
      // Rückmeldung "der Block hängt jetzt am Finger". Ein Mausdrag ist ohnehin
      // sichtbar und auf dem Desktop ist die Rückmeldung ein No-op.
      if (drag != null &&
          hapticFeedbackOnStart &&
          recognizer.lastPointerKind != PointerDeviceKind.mouse) {
        HapticFeedback.selectionClick();
      }
      return drag;
    };
    return recognizer;
  }
}

/// Erzeugt pro Zeiger den passenden Zustand: verzögert für Finger, sofort für Maus.
class _PointerAwareMultiDragGestureRecognizer extends MultiDragGestureRecognizer {
  _PointerAwareMultiDragGestureRecognizer({
    required this.delay,
    required this.mouseSlop,
    super.debugOwner,
  });

  final Duration delay;
  final double mouseSlop;

  /// Gerät des zuletzt begonnenen Drags — nur für die Haptik-Entscheidung oben.
  PointerDeviceKind? lastPointerKind;

  @override
  MultiDragPointerState createNewPointerState(PointerDownEvent event) {
    lastPointerKind = event.kind;
    return switch (event.kind) {
      PointerDeviceKind.mouse => _SlopPointerState(
          event.position,
          event.kind,
          gestureSettings,
          slop: mouseSlop,
        ),
      _ => _HoldPointerState(
          event.position,
          event.kind,
          gestureSettings,
          delay: delay,
        ),
    };
  }

  @override
  String get debugDescription => 'pointer-aware multidrag';
}

/// Sofortiger Drag ab einer frei wählbaren Mindeststrecke.
///
/// Entspricht dem `_ImmediatePointerState` des Frameworks, nur dass der Slop nicht
/// aus [computeHitSlop] kommt — für die Maus wäre das 1 px, und dann würde jeder
/// minimal verwackelte Klick zum Drag statt zum Tap.
class _SlopPointerState extends MultiDragPointerState {
  _SlopPointerState(super.initialPosition, super.kind, super.gestureSettings, {required this.slop});

  final double slop;

  @override
  void checkForResolutionAfterMove() {
    if (pendingDelta!.distance > slop) {
      resolve(GestureDisposition.accepted);
    }
  }

  @override
  void accepted(GestureMultiDragStartCallback starter) => starter(initialPosition);
}

/// Drag erst nach [delay] gedrückt Halten — Nachbau des frameworkeigenen,
/// aber privaten `_DelayedPointerState`.
class _HoldPointerState extends MultiDragPointerState {
  _HoldPointerState(super.initialPosition, super.kind, super.gestureSettings, {required Duration delay}) {
    _timer = Timer(delay, _delayPassed);
  }

  Timer? _timer;
  GestureMultiDragStartCallback? _starter;

  void _delayPassed() {
    _timer = null;
    if (_starter != null) {
      _starter!(initialPosition);
      _starter = null;
    } else {
      resolve(GestureDisposition.accepted);
    }
  }

  void _stopTimer() {
    _timer?.cancel();
    _timer = null;
  }

  @override
  void accepted(GestureMultiDragStartCallback starter) {
    if (_timer == null) {
      starter(initialPosition);
    } else {
      _starter = starter;
    }
  }

  @override
  void checkForResolutionAfterMove() {
    // Timer schon abgelaufen: der Drag läuft (oder wartet auf die Arena) — dann
    // darf Bewegung ihn nicht mehr abbrechen.
    if (_timer == null) return;
    if (pendingDelta!.distance > computeHitSlop(kind, gestureSettings)) {
      resolve(GestureDisposition.rejected);
      _stopTimer();
    }
  }

  @override
  void dispose() {
    _stopTimer();
    super.dispose();
  }
}
