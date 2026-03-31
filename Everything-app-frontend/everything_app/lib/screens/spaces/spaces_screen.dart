import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';

// Stitch Design System: "The Digital Curator"
const _surfaceContainerLow = Color(0xFFF6F3F2);
const _surfaceContainerLowest = Color(0xFFFFFFFF);
const _onSurface = Color(0xFF323232);
const _onSurfaceVariant = Color(0xFF5F5F5F);

final _cardShadow = BoxShadow(
  color: _onSurface.withOpacity(0.04),
  blurRadius: 32,
  offset: const Offset(0, 8),
);

class SpacesScreen extends StatelessWidget {
  const SpacesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    // Keep consistent colors with the rest of the application spaces
    final spaces = [
      _SpaceData(
        icon: Icons.task_alt,
        title: 'Aufgaben',
        status: '12 Active',
        route: '/tasks',
        color: const Color(0xFFF97316),
      ),
      _SpaceData(
        icon: Icons.sync,
        title: 'Habits',
        status: '85% Completion',
        route: '/tasks', // Routing to tasks or specific habits screen if exists
        color: const Color(0xFF06B6D4),
      ),
      _SpaceData(
        icon: Icons.note_alt_outlined,
        title: 'Notizen',
        status: 'Updated 2h ago',
        route: '/study', // Or specific notes route
        color: const Color(0xFF8B5CF6),
      ),
      _SpaceData(
        icon: Icons.school_outlined,
        title: 'Studium',
        status: 'Exam Prep Active',
        route: '/study',
        color: const Color(0xFF3B82F6),
      ),
      _SpaceData(
        icon: Icons.fitness_center_outlined,
        title: 'Gym',
        status: 'Leg Day Scheduled',
        route: '/sports',
        color: const Color(0xFFEC4899),
      ),
      _SpaceData(
        icon: Icons.restaurant_menu_outlined,
        title: 'Rezepte',
        status: '4 New Ideas',
        route: '/recipes',
        color: const Color(0xFF10B981),
      ),
      _SpaceData(
        icon: Icons.account_balance_wallet_outlined,
        title: 'Finanzen',
        status: 'On Track',
        route: '/finance',
        color: const Color(0xFFEAB308),
      ),
    ];

    return Scaffold(
      backgroundColor: _surfaceContainerLow,
      appBar: AppBar(
        title: Text(
          'Spaces',
          style: GoogleFonts.manrope(
            color: _onSurface,
            fontWeight: FontWeight.w800,
            fontSize: 24,
            letterSpacing: -0.5,
          ),
        ),
        backgroundColor: _surfaceContainerLow,
        elevation: 0,
        centerTitle: false,
        titleSpacing: 24,
      ),
      body: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
        child: GridView.builder(
          physics: const AlwaysScrollableScrollPhysics(),
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 2,
            mainAxisSpacing: 16,
            crossAxisSpacing: 16,
            childAspectRatio: 0.95,
          ),
          itemCount: spaces.length,
          itemBuilder: (context, index) {
            return _SpaceGridCard(space: spaces[index]);
          },
        ),
      ),
    );
  }
}

class _SpaceData {
  final IconData icon;
  final String title;
  final String status;
  final String route;
  final Color color;

  const _SpaceData({
    required this.icon,
    required this.title,
    required this.status,
    required this.route,
    required this.color,
  });
}

class _SpaceGridCard extends StatelessWidget {
  final _SpaceData space;
  const _SpaceGridCard({required this.space});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => context.go(space.route),
      child: Container(
        decoration: BoxDecoration(
          color: _surfaceContainerLowest,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [_cardShadow],
        ),
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: space.color.withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: Icon(space.icon, color: space.color, size: 28),
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  space.title,
                  style: GoogleFonts.manrope(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: _onSurface,
                    letterSpacing: -0.2,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  space.status,
                  style: GoogleFonts.inter(
                    fontSize: 12,
                    color: _onSurfaceVariant,
                    fontWeight: FontWeight.w500,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}