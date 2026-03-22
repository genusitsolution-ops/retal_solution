// lib/screens/profile_screen.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/auth_provider.dart';
import '../config/theme.dart';
import '../config/constants.dart';
import '../services/api_service.dart';
import 'login_screen.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  Map? _profile;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final auth = context.read<AuthProvider>();
    final url = auth.userType == 'tenant'
        ? ApiConfig.tenantProfile
        : auth.userType == 'employee'
            ? ApiConfig.empDashboard
            : ApiConfig.ownerDashboard;

    final res = await ApiService().get(url);
    if (!mounted) return;
    setState(() {
      _loading = false;
      _profile = auth.userData;
    });
  }

  Color get _roleColor {
    final auth = context.read<AuthProvider>();
    switch (auth.userType) {
      case 'owner':
        return AppTheme.primary;
      case 'employee':
        return AppTheme.accentOrange;
      case 'tenant':
        return AppTheme.accentTeal;
      default:
        return AppTheme.primary;
    }
  }

  String get _roleLabel {
    final auth = context.read<AuthProvider>();
    switch (auth.userType) {
      case 'owner':
        return 'Property Owner';
      case 'employee':
        return 'Field Agent';
      case 'tenant':
        return 'Tenant';
      default:
        return 'User';
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.read<AuthProvider>();
    final name = auth.userData?['full_name'] ?? 'User';
    final phone = auth.userData?['phone'] ?? '-';
    final email = auth.userData?['email'] ?? '-';
    final username = auth.userData?['username'] ?? '-';

    final initials = name
        .split(' ')
        .take(2)
        .map((s) => s.isNotEmpty ? s[0].toUpperCase() : '')
        .join();

    return Scaffold(
      backgroundColor: AppTheme.background,
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            expandedHeight: 220,
            pinned: true,
            backgroundColor: _roleColor,
            flexibleSpace: FlexibleSpaceBar(
              background: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [
                      _roleColor,
                      _roleColor.withOpacity(0.7)
                    ],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                ),
                child: SafeArea(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const SizedBox(height: 20),
                      CircleAvatar(
                        radius: 44,
                        backgroundColor:
                            Colors.white.withOpacity(0.2),
                        child: Text(
                          initials,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 28,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        name,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 14, vertical: 5),
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Text(
                          _roleLabel,
                          style: const TextStyle(
                              color: Colors.white, fontSize: 13),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            title: const Text('Profile',
                style: TextStyle(color: Colors.white)),
          ),
          SliverPadding(
            padding: const EdgeInsets.all(16),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                _InfoCard(title: 'Account Info', items: [
                  _InfoItem(
                      icon: Icons.person,
                      label: 'Username',
                      value: username),
                  _InfoItem(
                      icon: Icons.phone,
                      label: 'Phone',
                      value: phone),
                  _InfoItem(
                      icon: Icons.email,
                      label: 'Email',
                      value: email),
                ]),
                const SizedBox(height: 16),
                _InfoCard(title: 'App Settings', items: [
                  _InfoItem(
                    icon: Icons.lock_outline,
                    label: 'Change Password',
                    value: '',
                    onTap: () => _showChangePassword(),
                  ),
                  _InfoItem(
                    icon: Icons.notifications_outlined,
                    label: 'Notifications',
                    value: 'On',
                  ),
                  _InfoItem(
                    icon: Icons.info_outline,
                    label: 'App Version',
                    value: '1.0.0',
                  ),
                ]),
                const SizedBox(height: 24),
                // Logout
                ElevatedButton.icon(
                  onPressed: () => _confirmLogout(),
                  icon: const Icon(Icons.logout, color: Colors.white),
                  label: const Text('Logout',
                      style: TextStyle(color: Colors.white)),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.accent,
                    minimumSize: const Size(double.infinity, 52),
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(14)),
                  ),
                ),
                const SizedBox(height: 16),
                Center(
                  child: Text(
                    'PRMS v1.0 • Genus IT Solution',
                    style: TextStyle(
                        fontSize: 12, color: Colors.grey[400]),
                  ),
                ),
                const SizedBox(height: 20),
              ]),
            ),
          ),
        ],
      ),
    );
  }

  void _confirmLogout() {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16)),
        title: const Text('Logout?'),
        content: const Text('Are you sure you want to logout?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () async {
              Navigator.pop(context);
              await context.read<AuthProvider>().logout();
              if (!mounted) return;
              Navigator.of(context).pushAndRemoveUntil(
                MaterialPageRoute(
                    builder: (_) => const LoginScreen()),
                (r) => false,
              );
            },
            style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.accent),
            child: const Text('Logout',
                style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  void _showChangePassword() {
    final oldCtrl = TextEditingController();
    final newCtrl = TextEditingController();
    final confirmCtrl = TextEditingController();
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius:
              BorderRadius.vertical(top: Radius.circular(24)),
        ),
        padding: EdgeInsets.fromLTRB(
            20,
            20,
            20,
            MediaQuery.of(context).viewInsets.bottom + 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Change Password',
                style: TextStyle(
                    fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 20),
            TextField(
              controller: oldCtrl,
              obscureText: true,
              decoration: const InputDecoration(
                labelText: 'Current Password',
                prefixIcon:
                    Icon(Icons.lock, color: AppTheme.primary),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: newCtrl,
              obscureText: true,
              decoration: const InputDecoration(
                labelText: 'New Password',
                prefixIcon:
                    Icon(Icons.lock_open, color: AppTheme.primary),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: confirmCtrl,
              obscureText: true,
              decoration: const InputDecoration(
                labelText: 'Confirm Password',
                prefixIcon: Icon(Icons.lock_reset,
                    color: AppTheme.primary),
              ),
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: () async {
                if (newCtrl.text != confirmCtrl.text) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                        content: Text('Passwords do not match')),
                  );
                  return;
                }
                final api = ApiService();
                final res = await api.put(ApiConfig.tenantProfile, {
                  'current_password': oldCtrl.text,
                  'new_password': newCtrl.text,
                });
                if (!context.mounted) return;
                Navigator.pop(context);
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(
                        res.success ? 'Password updated!' : res.message),
                    backgroundColor: res.success
                        ? AppTheme.statusPaid
                        : AppTheme.accent,
                  ),
                );
              },
              style: ElevatedButton.styleFrom(
                  minimumSize: const Size(double.infinity, 50)),
              child: const Text('Update Password'),
            ),
          ],
        ),
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
        boxShadow: [
          BoxShadow(
              color: Colors.black.withOpacity(0.05),
              blurRadius: 10,
              offset: const Offset(0, 3))
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
            child: Text(title,
                style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: AppTheme.textGrey,
                    letterSpacing: 0.5)),
          ),
          const Divider(height: 1, color: AppTheme.divider),
          ...items.asMap().entries.map((e) => Column(
                children: [
                  e.value,
                  if (e.key < items.length - 1)
                    const Divider(
                        height: 1,
                        color: AppTheme.divider,
                        indent: 52),
                ],
              )),
        ],
      ),
    );
  }
}

class _InfoItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final VoidCallback? onTap;

  const _InfoItem({
    required this.icon,
    required this.label,
    required this.value,
    this.onTap,
  });

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
      title: Text(label,
          style: const TextStyle(
              fontSize: 13, color: AppTheme.textGrey)),
      subtitle: value.isNotEmpty
          ? Text(value,
              style: const TextStyle(
                  fontSize: 14,
                  color: AppTheme.textDark,
                  fontWeight: FontWeight.w500))
          : null,
      trailing: onTap != null
          ? const Icon(Icons.chevron_right,
              color: AppTheme.textGrey)
          : null,
      onTap: onTap,
    );
  }
}
