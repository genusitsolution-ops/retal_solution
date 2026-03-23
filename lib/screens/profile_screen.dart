// lib/screens/profile_screen.dart - with language switcher
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/auth_provider.dart';
import '../providers/language_provider.dart';
import '../config/app_strings.dart';
import '../config/theme.dart';
import '../config/constants.dart' as app_constants;
import '../services/api_service.dart';
import 'login_screen.dart';

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  Color _roleColor(String? type) {
    switch (type) {
      case 'owner': return AppTheme.primary;
      case 'employee': return AppTheme.accentOrange;
      case 'tenant': return AppTheme.accentTeal;
      default: return AppTheme.primary;
    }
  }

  String _roleLabel(String? type, LanguageProvider lang) {
    switch (type) {
      case 'owner': return 'Property Owner';
      case 'employee': return 'Field Agent';
      case 'tenant': return 'Tenant';
      default: return 'User';
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.read<AuthProvider>();
    final lang = context.watch<LanguageProvider>();
    final name = auth.userData?['full_name'] ?? 'User';
    final phone = auth.userData?['phone'] ?? '-';
    final email = auth.userData?['email'] ?? '-';
    final username = auth.userData?['username'] ?? '-';
    final userType = auth.userType;
    final color = _roleColor(userType);
    final initials = name.split(' ').take(2)
        .map((s) => s.isNotEmpty ? s[0].toUpperCase() : '').join();

    return Scaffold(
      backgroundColor: AppTheme.background,
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            expandedHeight: 220,
            pinned: true,
            backgroundColor: color,
            flexibleSpace: FlexibleSpaceBar(
              background: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [color, color.withOpacity(0.7)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                ),
                child: SafeArea(
                  child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                    const SizedBox(height: 20),
                    CircleAvatar(
                      radius: 44,
                      backgroundColor: Colors.white.withOpacity(0.2),
                      child: Text(initials, style: const TextStyle(
                          color: Colors.white, fontSize: 28, fontWeight: FontWeight.bold)),
                    ),
                    const SizedBox(height: 12),
                    Text(name, style: const TextStyle(
                        color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 4),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
                      decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(20)),
                      child: Text(_roleLabel(userType, lang),
                          style: const TextStyle(color: Colors.white, fontSize: 13)),
                    ),
                  ]),
                ),
              ),
            ),
            title: Text(lang.get('profile'), style: const TextStyle(color: Colors.white)),
          ),
          SliverPadding(
            padding: const EdgeInsets.all(16),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                // Account Info
                _InfoCard(title: lang.get('account_info'), items: [
                  _InfoItem(icon: Icons.person, label: lang.get('username'), value: username),
                  _InfoItem(icon: Icons.phone, label: lang.get('phone'), value: phone),
                  _InfoItem(icon: Icons.email, label: lang.get('email'), value: email),
                ]),
                const SizedBox(height: 16),
                // Language Switcher Card
                Container(
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(16),
                    boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05),
                        blurRadius: 10, offset: const Offset(0, 3))],
                  ),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
                      child: Text(lang.get('language'),
                          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600,
                              color: AppTheme.textGrey, letterSpacing: 0.5)),
                    ),
                    const Divider(height: 1, color: AppTheme.divider),
                    ...AppLanguage.values.map((l) {
                      final isSelected = lang.language == l;
                      return ListTile(
                        leading: Text(AppStrings.languageFlags[l]!,
                            style: const TextStyle(fontSize: 24)),
                        title: Text(AppStrings.languageNames[l]!,
                            style: TextStyle(fontWeight: isSelected
                                ? FontWeight.bold : FontWeight.normal,
                                color: isSelected ? AppTheme.primary : AppTheme.textDark)),
                        trailing: isSelected
                            ? const Icon(Icons.check_circle, color: AppTheme.primary)
                            : null,
                        onTap: () => lang.setLanguage(l),
                      );
                    }),
                  ]),
                ),
                const SizedBox(height: 16),
                // Settings
                _InfoCard(title: 'App Settings', items: [
                  _InfoItem(icon: Icons.lock_outline, label: lang.get('change_password'),
                      value: '', onTap: () => _showChangePwd(context, lang)),
                  _InfoItem(icon: Icons.info_outline, label: lang.get('app_version'), value: '1.0.0'),
                ]),
                const SizedBox(height: 24),
                // Logout
                ElevatedButton.icon(
                  onPressed: () => _confirmLogout(context, lang),
                  icon: const Icon(Icons.logout, color: Colors.white),
                  label: Text(lang.get('logout'), style: const TextStyle(color: Colors.white)),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.accent,
                    minimumSize: const Size(double.infinity, 52),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                ),
                const SizedBox(height: 16),
                Center(child: Text('PRMS v1.0 • Genus IT Solution',
                    style: TextStyle(fontSize: 12, color: Colors.grey[400]))),
                const SizedBox(height: 20),
              ]),
            ),
          ),
        ],
      ),
    );
  }

  void _confirmLogout(BuildContext context, LanguageProvider lang) {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Text(lang.get('logout')),
        content: Text(lang.get('logout_confirm')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context),
              child: Text(lang.get('cancel'))),
          ElevatedButton(
            onPressed: () async {
              Navigator.pop(context);
              await context.read<AuthProvider>().logout();
              if (!context.mounted) return;
              Navigator.of(context).pushAndRemoveUntil(
                  MaterialPageRoute(builder: (_) => const LoginScreen()),
                      (r) => false);
            },
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.accent),
            child: Text(lang.get('logout'), style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  void _showChangePwd(BuildContext context, LanguageProvider lang) {
    final oldCtrl = TextEditingController();
    final newCtrl = TextEditingController();
    final confirmCtrl = TextEditingController();
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => Container(
        decoration: const BoxDecoration(color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
        padding: EdgeInsets.fromLTRB(20, 20, 20,
            MediaQuery.of(context).viewInsets.bottom + 20),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Text(lang.get('change_password'),
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 20),
          TextField(controller: oldCtrl, obscureText: true,
              decoration: const InputDecoration(labelText: 'Current Password',
                  prefixIcon: Icon(Icons.lock, color: AppTheme.primary))),
          const SizedBox(height: 12),
          TextField(controller: newCtrl, obscureText: true,
              decoration: const InputDecoration(labelText: 'New Password',
                  prefixIcon: Icon(Icons.lock_open, color: AppTheme.primary))),
          const SizedBox(height: 12),
          TextField(controller: confirmCtrl, obscureText: true,
              decoration: const InputDecoration(labelText: 'Confirm Password',
                  prefixIcon: Icon(Icons.lock_reset, color: AppTheme.primary))),
          const SizedBox(height: 20),
          ElevatedButton(
            onPressed: () async {
              if (newCtrl.text != confirmCtrl.text) {
                ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Passwords do not match')));
                return;
              }
              final api = ApiService();
              final res = await api.put(app_constants.ApiConfig.tenantProfile, {
                'current_password': oldCtrl.text,
                'new_password': newCtrl.text,
              });
              if (!context.mounted) return;
              Navigator.pop(context);
              ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                content: Text(res.success ? 'Password updated!' : res.message),
                backgroundColor: res.success ? AppTheme.statusPaid : AppTheme.accent,
              ));
            },
            style: ElevatedButton.styleFrom(minimumSize: const Size(double.infinity, 50)),
            child: Text(lang.get('save')),
          ),
        ]),
      ),
    );
  }
}

class _InfoCard extends StatelessWidget {
  final String title;
  final List<_InfoItem> items;
  const _InfoCard({required this.title, required this.items});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05),
            blurRadius: 10, offset: const Offset(0, 3))],
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
          child: Text(title, style: const TextStyle(fontSize: 13,
              fontWeight: FontWeight.w600, color: AppTheme.textGrey, letterSpacing: 0.5)),
        ),
        const Divider(height: 1, color: AppTheme.divider),
        ...items.asMap().entries.map((e) => Column(children: [
          e.value,
          if (e.key < items.length - 1)
            const Divider(height: 1, color: AppTheme.divider, indent: 52),
        ])),
      ]),
    );
  }
}

class _InfoItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final VoidCallback? onTap;
  const _InfoItem({required this.icon, required this.label, required this.value, this.onTap});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Container(
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: AppTheme.primary.withOpacity(0.08),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Icon(icon, size: 18, color: AppTheme.primary),
      ),
      title: Text(label, style: const TextStyle(fontSize: 13, color: AppTheme.textGrey)),
      subtitle: value.isNotEmpty ? Text(value, style: const TextStyle(fontSize: 14,
          color: AppTheme.textDark, fontWeight: FontWeight.w500)) : null,
      trailing: onTap != null ? const Icon(Icons.chevron_right, color: AppTheme.textGrey) : null,
      onTap: onTap,
    );
  }
}
