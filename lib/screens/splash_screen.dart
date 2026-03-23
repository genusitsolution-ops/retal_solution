// lib/screens/splash_screen.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../providers/auth_provider.dart';
import '../providers/language_provider.dart';
import '../config/theme.dart';
import 'language_select_screen.dart';
import 'login_screen.dart';
import 'owner/owner_home.dart';
import 'employee/employee_home.dart';
import 'tenant/tenant_home.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});
  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _scaleAnim;
  late Animation<double> _fadeAnim;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 1200));
    _scaleAnim = Tween<double>(begin: 0.4, end: 1.0)
        .animate(CurvedAnimation(parent: _ctrl, curve: Curves.elasticOut));
    _fadeAnim = Tween<double>(begin: 0.0, end: 1.0)
        .animate(CurvedAnimation(parent: _ctrl, curve: const Interval(0.4, 1.0)));
    _ctrl.forward();
    Future.delayed(const Duration(seconds: 2), _navigate);
  }

  Future<void> _navigate() async {
    if (!mounted) return;
    final auth = context.read<AuthProvider>();
    auth.restoreSession();

    // Check if language has been selected before
    final prefs = await SharedPreferences.getInstance();
    final langSaved = prefs.getString('app_language');

    if (!mounted) return;

    // First time — show language selection
    if (langSaved == null) {
      Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const LanguageSelectScreen()));
      return;
    }

    // Not logged in
    if (!auth.isLoggedIn) {
      Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const LoginScreen()));
      return;
    }

    // Go to role-based home
    Widget next;
    switch (auth.userType) {
      case 'owner': next = const OwnerHome(); break;
      case 'employee': next = const EmployeeHome(); break;
      case 'tenant': next = const TenantHome(); break;
      default: next = const LoginScreen();
    }
    Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => next));
  }

  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }

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
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              ScaleTransition(
                scale: _scaleAnim,
                child: Container(
                  width: 100, height: 100,
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(28),
                    boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.3),
                        blurRadius: 30, offset: const Offset(0, 10))],
                  ),
                  child: const Center(child: Text('P', style: TextStyle(
                      fontSize: 48, fontWeight: FontWeight.bold, color: AppTheme.primary))),
                ),
              ),
              const SizedBox(height: 28),
              FadeTransition(
                opacity: _fadeAnim,
                child: const Column(children: [
                  Text('PRMS', style: TextStyle(color: Colors.white, fontSize: 32,
                      fontWeight: FontWeight.bold, letterSpacing: 4)),
                  SizedBox(height: 8),
                  Text('Property Rental Management',
                      style: TextStyle(color: Colors.white60, fontSize: 14, letterSpacing: 1)),
                ]),
              ),
              const SizedBox(height: 60),
              FadeTransition(
                opacity: _fadeAnim,
                child: const SizedBox(width: 32, height: 32,
                    child: CircularProgressIndicator(color: Colors.white54, strokeWidth: 2)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
