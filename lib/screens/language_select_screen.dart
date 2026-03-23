// lib/screens/language_select_screen.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../config/app_strings.dart';
import '../config/theme.dart';
import '../providers/language_provider.dart';
import 'login_screen.dart';

class LanguageSelectScreen extends StatefulWidget {
  const LanguageSelectScreen({super.key});
  @override
  State<LanguageSelectScreen> createState() => _LanguageSelectScreenState();
}

class _LanguageSelectScreenState extends State<LanguageSelectScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _fadeAnim;
  late Animation<Offset> _slideAnim;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 800));
    _fadeAnim = Tween<double>(begin: 0, end: 1).animate(_ctrl);
    _slideAnim = Tween<Offset>(begin: const Offset(0, 0.3), end: Offset.zero)
        .animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeOutCubic));
    _ctrl.forward();
  }

  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }

  Future<void> _select(AppLanguage lang) async {
    await context.read<LanguageProvider>().setLanguage(lang);
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
        MaterialPageRoute(builder: (_) => const LoginScreen()));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [AppTheme.primary, Color(0xFF283593)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: SafeArea(
          child: FadeTransition(
            opacity: _fadeAnim,
            child: SlideTransition(
              position: _slideAnim,
              child: Column(
                children: [
                  const SizedBox(height: 60),
                  // Logo
                  Container(
                    width: 90, height: 90,
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(24),
                      boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.2),
                          blurRadius: 20, offset: const Offset(0, 8))],
                    ),
                    child: const Center(
                      child: Text('P', style: TextStyle(fontSize: 44,
                          fontWeight: FontWeight.bold, color: AppTheme.primary)),
                    ),
                  ),
                  const SizedBox(height: 20),
                  const Text('PRMS', style: TextStyle(color: Colors.white,
                      fontSize: 28, fontWeight: FontWeight.bold, letterSpacing: 4)),
                  const SizedBox(height: 8),
                  const Text('Property Rental Management',
                      style: TextStyle(color: Colors.white60, fontSize: 13)),
                  const SizedBox(height: 60),
                  // Language selection card
                  Container(
                    margin: const EdgeInsets.symmetric(horizontal: 24),
                    padding: const EdgeInsets.all(28),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(24),
                      boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.15),
                          blurRadius: 24, offset: const Offset(0, 8))],
                    ),
                    child: Column(
                      children: [
                        const Text(
                          'भाषा निवडा / भाषा चुनें\nSelect Language',
                          textAlign: TextAlign.center,
                          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold,
                              color: AppTheme.textDark, height: 1.5),
                        ),
                        const SizedBox(height: 8),
                        const Text(
                          'You can change language anytime from Profile',
                          textAlign: TextAlign.center,
                          style: TextStyle(fontSize: 12, color: AppTheme.textGrey),
                        ),
                        const SizedBox(height: 28),
                        _LangBtn(
                          emoji: '🇬🇧',
                          name: 'English',
                          subtitle: 'English Language',
                          color: const Color(0xFF1565C0),
                          onTap: () => _select(AppLanguage.english),
                        ),
                        const SizedBox(height: 14),
                        _LangBtn(
                          emoji: '🇮🇳',
                          name: 'हिंदी',
                          subtitle: 'Hindi Language',
                          color: const Color(0xFFE65100),
                          onTap: () => _select(AppLanguage.hindi),
                        ),
                        const SizedBox(height: 14),
                        _LangBtn(
                          emoji: '🏵️',
                          name: 'मराठी',
                          subtitle: 'Marathi Language',
                          color: const Color(0xFF2E7D32),
                          onTap: () => _select(AppLanguage.marathi),
                        ),
                      ],
                    ),
                  ),
                  const Spacer(),
                  Text('PRMS v1.0 • Genus IT Solution',
                      style: TextStyle(color: Colors.white.withOpacity(0.4), fontSize: 11)),
                  const SizedBox(height: 16),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _LangBtn extends StatelessWidget {
  final String emoji;
  final String name;
  final String subtitle;
  final Color color;
  final VoidCallback onTap;

  const _LangBtn({required this.emoji, required this.name, required this.subtitle,
      required this.color, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: color.withOpacity(0.06),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: color.withOpacity(0.25), width: 1.5),
        ),
        child: Row(children: [
          Text(emoji, style: const TextStyle(fontSize: 32)),
          const SizedBox(width: 16),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(name, style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: color)),
            Text(subtitle, style: const TextStyle(fontSize: 12, color: AppTheme.textGrey)),
          ])),
          Icon(Icons.arrow_forward_ios, size: 16, color: color),
        ]),
      ),
    );
  }
}
