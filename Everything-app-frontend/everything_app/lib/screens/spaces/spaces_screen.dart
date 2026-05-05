import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';

// Kinetic Mono Dark Theme
const _backgroundColor    = Color(0xFF0E0E0E);
const _cardColor          = Color(0xFF252626);
const _onSurface          = Color(0xFFE7E5E5);
const _onSurfaceVariant   = Color(0xFFACABAA);
const _primaryColor       = Color(0xFFC2C1FF);
const _appBarColor        = Color(0xFF131313);

class SpacesScreen extends StatelessWidget {
  const SpacesScreen({super.key});

  static const _spaces = [
    _SpaceData(
      icon: Icons.checklist,
      title: 'Aufgaben',
      statusLabel: 'ACTIVE',
      statusSub: '12 Active',
      route: '/tasks',
      accentColor: Color(0xFF5856D6),
    ),
    _SpaceData(
      icon: Icons.refresh,
      title: 'Habits',
      statusLabel: 'TODAY',
      statusSub: '85% Completion',
      route: '/tasks',
      accentColor: Color(0xFF2DD4BF),
    ),
    _SpaceData(
      icon: Icons.description_outlined,
      title: 'Notizen',
      statusLabel: 'RECENT',
      statusSub: 'Updated 2h ago',
      route: '/study',
      accentColor: Color(0xFFF59E0B),
    ),
    _SpaceData(
      icon: Icons.school,
      title: 'Studium',
      statusLabel: 'PHASE',
      statusSub: 'Exam Prep Active',
      route: '/study',
      accentColor: Color(0xFF3B82F6),
    ),
    _SpaceData(
      icon: Icons.fitness_center,
      title: 'Gym',
      statusLabel: 'TRACK',
      statusSub: 'Leg Day Scheduled',
      route: '/sports',
      accentColor: Color(0xFFF97316),
    ),
    _SpaceData(
      icon: Icons.restaurant,
      title: 'Rezepte',
      statusLabel: 'MEAL PLAN',
      statusSub: '4 New Ideas',
      route: '/recipes',
      accentColor: Color(0xFF4ADE80),
    ),
    _SpaceData(
      icon: Icons.account_balance_wallet,
      title: 'Finanzen',
      statusLabel: 'BUDGET',
      statusSub: 'On Track',
      route: '/finance',
      accentColor: Color(0xFFA855F7),
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _backgroundColor,
      appBar: AppBar(
        backgroundColor: _appBarColor,
        elevation: 0,
        titleSpacing: 0,
        leading: IconButton(
          icon: const Icon(Icons.menu, color: _primaryColor),
          onPressed: () {},
        ),
        title: Text(
          'Spaces',
          style: GoogleFonts.manrope(
            color: _primaryColor,
            fontWeight: FontWeight.w800,
            fontSize: 20,
            letterSpacing: 0,
          ),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.search, color: _primaryColor),
            onPressed: () {},
          ),
        ],
      ),
      body: LayoutBuilder(
        builder: (context, constraints) {
          // 1 col on narrow, 2 on medium, 3 on wide — matching the HTML breakpoints
          int columns = 1;
          if (constraints.maxWidth >= 900) columns = 3;
          else if (constraints.maxWidth >= 600) columns = 2;

          return GridView.builder(
            padding: const EdgeInsets.fromLTRB(24, 24, 24, 100),
            gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: columns,
              mainAxisSpacing: 16,
              crossAxisSpacing: 24,
              // Taller cards on single column to match Stitch proportions
              mainAxisExtent: 180,
            ),
            itemCount: _spaces.length,
            itemBuilder: (context, index) =>
                _SpaceGridCard(space: _spaces[index]),
          );
        },
      ),
    );
  }
}

// ─── Space Data ───────────────────────────────────────────────────────────────
class _SpaceData {
  final IconData icon;
  final String title;
  final String statusLabel; // top-right tag e.g. "ACTIVE"
  final String statusSub;   // bottom subtitle e.g. "12 Active"
  final String route;
  final Color accentColor;

  const _SpaceData({
    required this.icon,
    required this.title,
    required this.statusLabel,
    required this.statusSub,
    required this.route,
    required this.accentColor,
  });
}

// ─── Space Grid Card (with hover) ────────────────────────────────────────────
class _SpaceGridCard extends StatefulWidget {
  final _SpaceData space;
  const _SpaceGridCard({required this.space});

  @override
  State<_SpaceGridCard> createState() => _SpaceGridCardState();
}

class _SpaceGridCardState extends State<_SpaceGridCard> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: GestureDetector(
        onTap: () => context.go(widget.space.route),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOut,
          // Lift effect: translate -4px on hover (matches hover:translate-y-[-4px])
          transform: Matrix4.translationValues(0, _hovered ? -4 : 0, 0),
          decoration: BoxDecoration(
            color: _hovered
                ? Color.lerp(_cardColor, widget.space.accentColor, 0.06)
                : _cardColor,
            // Accent top border — the signature detail from the Stitch design
            border: Border(
              top: BorderSide(color: widget.space.accentColor, width: 2),
            ),
          ),
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              // Top row: icon left, status label right
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    widget.space.icon,
                    color: widget.space.accentColor,
                    size: 28,
                  ),
                  Text(
                    widget.space.statusLabel,
                    style: GoogleFonts.manrope(
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      color: _onSurfaceVariant,
                      letterSpacing: 1.5,
                    ),
                  ),
                ],
              ),
              // Bottom: title + subtitle
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    widget.space.title,
                    style: GoogleFonts.manrope(
                      fontSize: 22,
                      fontWeight: FontWeight.w800,
                      color: _hovered ? _primaryColor : _onSurface,
                      letterSpacing: -0.3,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    widget.space.statusSub,
                    style: GoogleFonts.manrope(
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: _onSurfaceVariant,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}