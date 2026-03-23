// lib/screens/login_screen.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/auth_provider.dart';
import '../providers/language_provider.dart';
import '../config/app_strings.dart';
import '../config/theme.dart';
import 'owner/owner_home.dart';
import 'employee/employee_home.dart';
import 'tenant/tenant_home.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen>
    with SingleTickerProviderStateMixin {
  final _formKey = GlobalKey<FormState>();
  final _usernameCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();
  bool _obscure = true;
  late AnimationController _animCtrl;
  late Animation<Offset> _slideAnim;
  late Animation<double> _fadeAnim;

  @override
  void initState() {
    super.initState();
    _animCtrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 800));
    _slideAnim = Tween<Offset>(begin: const Offset(0, 0.3), end: Offset.zero)
        .animate(CurvedAnimation(parent: _animCtrl, curve: Curves.easeOutCubic));
    _fadeAnim = Tween<double>(begin: 0, end: 1).animate(_animCtrl);
    _animCtrl.forward();
  }

  @override
  void dispose() {
    _animCtrl.dispose(); _usernameCtrl.dispose(); _passwordCtrl.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    if (!_formKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    final auth = context.read<AuthProvider>();
    final success = await auth.login(_usernameCtrl.text.trim(), _passwordCtrl.text);
    if (!mounted) return;
    if (success) {
      Widget next;
      switch (auth.userType) {
        case 'owner': next = const OwnerHome(); break;
        case 'employee': next = const EmployeeHome(); break;
        case 'tenant': next = const TenantHome(); break;
        default: next = const OwnerHome();
      }
      Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => next));
    } else {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(auth.error ?? 'Login failed'),
        backgroundColor: AppTheme.accent,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ));
    }
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;
    final lang = context.watch<LanguageProvider>();
    return Scaffold(
      body: Stack(children: [
        Container(height: size.height * 0.42,
          decoration: const BoxDecoration(gradient: LinearGradient(
            colors: [AppTheme.primary, Color(0xFF283593)],
            begin: Alignment.topLeft, end: Alignment.bottomRight))),
        Positioned(top: -40, right: -40, child: Container(width: 180, height: 180,
            decoration: BoxDecoration(shape: BoxShape.circle, color: Colors.white.withOpacity(0.06)))),
        SafeArea(
          child: FadeTransition(opacity: _fadeAnim,
            child: SlideTransition(position: _slideAnim,
              child: Column(children: [
                const SizedBox(height: 40),
                Container(width: 80, height: 80,
                  decoration: BoxDecoration(color: Colors.white,
                    borderRadius: BorderRadius.circular(22),
                    boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.2), blurRadius: 20, offset: const Offset(0, 8))]),
                  child: const Center(child: Text('P', style: TextStyle(fontSize: 38, fontWeight: FontWeight.bold, color: AppTheme.primary)))),
                const SizedBox(height: 16),
                const Text('PRMS', style: TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold, letterSpacing: 3)),
                const SizedBox(height: 4),
                Text(lang.get('property_rental_management'), style: const TextStyle(color: Colors.white60, fontSize: 13)),
                const SizedBox(height: 28),
                Expanded(
                  child: Container(
                    margin: const EdgeInsets.symmetric(horizontal: 20),
                    padding: const EdgeInsets.all(24),
                    decoration: BoxDecoration(color: Colors.white,
                      borderRadius: BorderRadius.circular(28),
                      boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.12), blurRadius: 30, offset: const Offset(0, -4))]),
                    child: SingleChildScrollView(
                      child: Form(key: _formKey, child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(lang.get('welcome_back'), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: AppTheme.textDark)),
                        const SizedBox(height: 4),
                        Text(lang.get('sign_in_continue'), style: const TextStyle(fontSize: 14, color: AppTheme.textGrey)),
                        const SizedBox(height: 24),
                        TextFormField(
                          controller: _usernameCtrl, textInputAction: TextInputAction.next,
                          decoration: InputDecoration(labelText: lang.get('username'),
                            prefixIcon: const Icon(Icons.person_outline, color: AppTheme.primary),
                            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                            filled: true, fillColor: const Color(0xFFF0F2F5)),
                          validator: (v) => v == null || v.isEmpty ? lang.get('required') : null,
                        ),
                        const SizedBox(height: 14),
                        TextFormField(
                          controller: _passwordCtrl, obscureText: _obscure,
                          textInputAction: TextInputAction.done,
                          onFieldSubmitted: (_) => _login(),
                          decoration: InputDecoration(
                            labelText: lang.get('password'),
                            prefixIcon: const Icon(Icons.lock_outline, color: AppTheme.primary),
                            suffixIcon: IconButton(
                              icon: Icon(_obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined, color: AppTheme.textGrey),
                              onPressed: () => setState(() => _obscure = !_obscure)),
                            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                            filled: true, fillColor: const Color(0xFFF0F2F5)),
                          validator: (v) => v == null || v.isEmpty ? lang.get('required') : null,
                        ),
                        const SizedBox(height: 10),
                        Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(color: AppTheme.primary.withOpacity(0.06), borderRadius: BorderRadius.circular(10)),
                          child: Row(children: [
                            const Icon(Icons.info_outline, size: 16, color: AppTheme.primary),
                            const SizedBox(width: 8),
                            Expanded(child: Text(lang.get('role_auto'), style: const TextStyle(fontSize: 12, color: AppTheme.primary))),
                          ]),
                        ),
                        const SizedBox(height: 20),
                        Consumer<AuthProvider>(builder: (_, auth, __) => ElevatedButton(
                          onPressed: auth.isLoading ? null : _login,
                          style: ElevatedButton.styleFrom(
                            minimumSize: const Size(double.infinity, 52),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                            backgroundColor: AppTheme.primary),
                          child: auth.isLoading
                              ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.5))
                              : Text(lang.get('sign_in'), style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white, letterSpacing: 1)),
                        )),
                        const SizedBox(height: 12),
                        // Language switcher
                        Center(child: TextButton.icon(
                          onPressed: () => _showLangSheet(context, lang),
                          icon: Text(AppStrings.languageFlags[lang.language]!, style: const TextStyle(fontSize: 16)),
                          label: Text('${AppStrings.languageNames[lang.language]} ▼',
                              style: const TextStyle(color: AppTheme.textGrey, fontSize: 12)),
                        )),
                      ])),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
              ]),
            ),
          ),
        ),
      ]),
    );
  }

  void _showLangSheet(BuildContext context, LanguageProvider lang) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => Container(
        decoration: const BoxDecoration(color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
        padding: const EdgeInsets.all(20),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Text(lang.get('select_language'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          ...AppLanguage.values.map((l) => ListTile(
            leading: Text(AppStrings.languageFlags[l]!, style: const TextStyle(fontSize: 26)),
            title: Text(AppStrings.languageNames[l]!,
                style: TextStyle(fontWeight: lang.language == l ? FontWeight.bold : FontWeight.normal,
                    color: lang.language == l ? AppTheme.primary : AppTheme.textDark)),
            trailing: lang.language == l ? const Icon(Icons.check_circle, color: AppTheme.primary) : null,
            onTap: () { Navigator.pop(context); lang.setLanguage(l); },
          )),
          const SizedBox(height: 8),
        ]),
      ),
    );
  }
}
