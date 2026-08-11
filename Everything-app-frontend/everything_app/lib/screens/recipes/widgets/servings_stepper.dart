import 'package:flutter/material.dart';

import '../../../theme/kinetic_theme.dart';

/// Der Portionsregler: − 4 +.
///
/// Die Zahl steht in [KineticTheme.amount] und damit in tabellarischen Ziffern -
/// beim Umstellen von 9 auf 10 springt daneben nichts in der Breite.
class ServingsStepper extends StatelessWidget {
  const ServingsStepper({
    super.key,
    required this.value,
    required this.onChanged,
    this.min = 1,
    this.max = 50,
  });

  final int value;
  final ValueChanged<int> onChanged;
  final int min;
  final int max;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Portionen',
      value: '$value',
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _button(Icons.remove, value > min ? () => onChanged(value - 1) : null,
              'Weniger Portionen'),
          SizedBox(
            width: 36,
            child: Text(
              '$value',
              textAlign: TextAlign.center,
              style: KineticTheme.amount,
            ),
          ),
          _button(Icons.add, value < max ? () => onChanged(value + 1) : null,
              'Mehr Portionen'),
        ],
      ),
    );
  }

  Widget _button(IconData icon, VoidCallback? onTap, String tooltip) {
    return Semantics(
      button: true,
      label: tooltip,
      child: InkWell(
        onTap: onTap,
        child: Container(
          width: 32,
          height: 32,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            border: Border.all(
              color: onTap == null ? KineticTheme.divider : KineticTheme.primary,
            ),
          ),
          child: Icon(
            icon,
            size: 16,
            color: onTap == null ? KineticTheme.textTertiary : KineticTheme.primary,
          ),
        ),
      ),
    );
  }
}
