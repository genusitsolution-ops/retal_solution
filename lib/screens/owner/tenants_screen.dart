import 'dart:io';
import 'package:image_picker/image_picker.dart';
// lib/screens/owner/tenants_screen.dart
import 'package:flutter/material.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class TenantsScreen extends StatefulWidget {
  const TenantsScreen({super.key});
  @override
  State<TenantsScreen> createState() => _TenantsScreenState();
}

class _TenantsScreenState extends State<TenantsScreen> {
  final ApiService _api = ApiService();
  bool _loading = true;
  String? _error;
  List _tenants = [];
  String _search = '';

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    final res = await _api.get(ApiConfig.ownerTenants);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (res.success && res.data != null) {
        _tenants = res.data is List ? res.data : (res.data['tenants'] ?? [res.data]);
      } else {
        _error = res.message.isNotEmpty ? res.message : 'Failed to load tenants';
      }
    });
  }

  List get _filtered {
    if (_search.isEmpty) return _tenants;
    final q = _search.toLowerCase();
    return _tenants.where((t) =>
      (t['full_name'] ?? '').toLowerCase().contains(q) ||
      (t['phone'] ?? '').contains(q) ||
      (t['property_code'] ?? '').toLowerCase().contains(q)
    ).toList();
  }

  Future<void> _delete(dynamic id) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Delete Tenant?'),
        content: const Text('This action cannot be undone.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          ElevatedButton(
            onPressed: () => Navigator.pop(context, true),
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.accent),
            child: const Text('Delete', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
    if (confirm != true) return;
    final res = await _api.delete('${ApiConfig.ownerTenants}?id=$id');
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(res.success ? 'Tenant deleted!' : res.message),
      backgroundColor: res.success ? AppTheme.statusPaid : AppTheme.accent,
    ));
    if (res.success) _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.primary,
        title: const Text('Tenants', style: TextStyle(color: Colors.white)),
        flexibleSpace: Container(decoration: const BoxDecoration(
          gradient: LinearGradient(colors: [AppTheme.primary, AppTheme.primaryLight],
              begin: Alignment.topLeft, end: Alignment.bottomRight),
        )),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(56),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
            child: TextField(
              onChanged: (v) => setState(() => _search = v),
              style: const TextStyle(color: Colors.white),
              decoration: InputDecoration(
                hintText: 'Search tenants...',
                hintStyle: const TextStyle(color: Colors.white54),
                prefixIcon: const Icon(Icons.search, color: Colors.white54),
                filled: true,
                fillColor: Colors.white.withOpacity(0.15),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                contentPadding: const EdgeInsets.symmetric(vertical: 10),
              ),
            ),
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showForm(),
        backgroundColor: AppTheme.primary,
        icon: const Icon(Icons.person_add, color: Colors.white),
        label: const Text('Add Tenant', style: TextStyle(color: Colors.white)),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: AppTheme.primary))
          : _error != null
              ? ErrorView(message: _error!, onRetry: _load)
              : _filtered.isEmpty
                  ? EmptyState(
                      icon: Icons.people_outline,
                      title: 'No Tenants Found',
                      actionLabel: 'Add Tenant',
                      onAction: () => _showForm(),
                    )
                  : RefreshIndicator(
                      onRefresh: _load,
                      child: ListView.builder(
                        padding: const EdgeInsets.fromLTRB(16, 16, 16, 80),
                        itemCount: _filtered.length,
                        itemBuilder: (_, i) {
                          final t = _filtered[i];
                          final initials = (t['full_name'] ?? 'T')
                              .split(' ').take(2).map((s) => s.isNotEmpty ? s[0].toUpperCase() : '').join();
                          return Container(
                            margin: const EdgeInsets.only(bottom: 10),
                            decoration: BoxDecoration(
                              color: Colors.white,
                              borderRadius: BorderRadius.circular(14),
                              boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 8, offset: const Offset(0, 2))],
                            ),
                            child: ListTile(
                              contentPadding: const EdgeInsets.all(14),
                              leading: CircleAvatar(
                                backgroundColor: AppTheme.primary.withOpacity(0.15),
                                radius: 24,
                                child: Text(initials, style: const TextStyle(color: AppTheme.primary, fontWeight: FontWeight.bold, fontSize: 15)),
                              ),
                              title: Text(t['full_name'] ?? 'Unknown',
                                  style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14, color: AppTheme.textDark)),
                              subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                const SizedBox(height: 3),
                                Row(children: [
                                  const Icon(Icons.apartment_outlined, size: 12, color: AppTheme.textGrey),
                                  const SizedBox(width: 3),
                                  Flexible(child: Text(
                                    t['property_code'] ?? t['allocated_property'] ?? 'Not allocated',
                                    style: const TextStyle(fontSize: 12),
                                    overflow: TextOverflow.ellipsis,
                                  )),
                                ]),
                                const SizedBox(height: 2),
                                Row(children: [
                                  const Icon(Icons.phone_outlined, size: 12, color: AppTheme.textGrey),
                                  const SizedBox(width: 3),
                                  Text(t['phone'] ?? '', style: const TextStyle(fontSize: 12, color: AppTheme.textGrey)),
                                ]),
                              ]),
                              trailing: Column(mainAxisAlignment: MainAxisAlignment.center,
                                  crossAxisAlignment: CrossAxisAlignment.end, children: [
                                StatusBadge(status: t['status'] ?? t['allocation_status'] ?? 'active'),
                                const SizedBox(height: 4),
                                Text(
                                  '₹${t['rent'] ?? t['base_rent'] ?? t['rent_amount'] ?? t['monthly_rent'] ?? t['amount'] ?? 0}/mo',
                                  style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppTheme.primary),
                                ),
                              ]),
                              onTap: () => _showDetail(t),
                              onLongPress: () => _showOptions(t),
                            ),
                          );
                        },
                      ),
                    ),
    );
  }

  void _showOptions(Map t) {
    showModalBottomSheet(
      context: context,
      builder: (_) => Column(mainAxisSize: MainAxisSize.min, children: [
        ListTile(leading: const Icon(Icons.edit, color: AppTheme.primary),
            title: const Text('Edit Tenant'), onTap: () { Navigator.pop(context); _showForm(tenant: t); }),
        ListTile(leading: const Icon(Icons.delete, color: AppTheme.accent),
            title: const Text('Delete Tenant'), onTap: () { Navigator.pop(context); _delete(t['id']); }),
      ]),
    );
  }

  void _showDetail(Map t) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _TenantDetail(tenant: t),
    );
  }

  void _showForm({Map? tenant}) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _TenantForm(tenant: tenant, onSaved: _load),
    );
  }
}

class _TenantDetail extends StatelessWidget {
  final Map tenant;
  const _TenantDetail({required this.tenant});
  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      padding: const EdgeInsets.all(20),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Container(width: 40, height: 4,
            decoration: BoxDecoration(color: Colors.grey[300], borderRadius: BorderRadius.circular(2))),
        const SizedBox(height: 16),
        Text(tenant['full_name'] ?? '', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        const SizedBox(height: 16),
        _row(Icons.phone, 'Phone', tenant['phone'] ?? '-'),
        _row(Icons.email, 'Email', tenant['email'] ?? '-'),
        _row(Icons.apartment, 'Property', tenant['property_code'] ?? tenant['allocated_property'] ?? '-'),
        _row(Icons.currency_rupee, 'Rent', '₹${tenant['rent'] ?? tenant['base_rent'] ?? 0}/month'),
        _row(Icons.badge, 'Aadhaar', tenant['aadhaar_number'] ?? '-'),
        _row(Icons.info, 'Status', tenant['status'] ?? '-'),
      ]),
    );
  }

  Widget _row(IconData icon, String label, String value) {
    return ListTile(
      leading: Icon(icon, color: AppTheme.primary, size: 20),
      title: Text(label, style: const TextStyle(fontSize: 12, color: AppTheme.textGrey)),
      subtitle: Text(value, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500, color: AppTheme.textDark)),
      dense: true,
    );
  }
}

class _TenantForm extends StatefulWidget {
  final Map? tenant;
  final VoidCallback onSaved;
  const _TenantForm({this.tenant, required this.onSaved});
  @override
  State<_TenantForm> createState() => _TenantFormState();
}

class _TenantFormState extends State<_TenantForm> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _name, _phone, _email, _aadhaar, _username, _password;
  bool _saving = false;
  File? _aadhaarFile;
  String? _aadhaarFileName;
  bool get _isEdit => widget.tenant != null;

  @override
  void initState() {
    super.initState();
    final t = widget.tenant;
    _name = TextEditingController(text: t?['full_name'] ?? '');
    _phone = TextEditingController(text: t?['phone'] ?? '');
    _email = TextEditingController(text: t?['email'] ?? '');
    _aadhaar = TextEditingController(text: t?['aadhaar_number'] ?? '');
    _username = TextEditingController(text: t?['username'] ?? '');
    _password = TextEditingController();
  }

  @override
  void dispose() {
    _name.dispose(); _phone.dispose(); _email.dispose();
    _aadhaar.dispose(); _username.dispose(); _password.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    final api = ApiService();
    final body = {
      'full_name': _name.text.trim(),
      'phone': _phone.text.trim(),
      'email': _email.text.trim(),
      'aadhaar_number': _aadhaar.text.trim(),
      'username': _username.text.trim(),
      if (!_isEdit || _password.text.isNotEmpty) 'password': _password.text,
    };
    ApiResponse res;
    if (_isEdit) {
      res = await api.put('${ApiConfig.ownerTenants}?id=${widget.tenant!['id']}', body);
    } else {
      res = await api.post(ApiConfig.ownerTenants, body);
    }
    if (!mounted) return;
    setState(() => _saving = false);
    Navigator.pop(context);
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(res.success ? (_isEdit ? 'Tenant updated!' : 'Tenant added!') : res.message),
      backgroundColor: res.success ? AppTheme.statusPaid : AppTheme.accent,
    ));
    if (res.success) widget.onSaved();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      padding: EdgeInsets.fromLTRB(20, 20, 20, MediaQuery.of(context).viewInsets.bottom + 20),
      child: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
            Center(child: Container(width: 40, height: 4,
                decoration: BoxDecoration(color: Colors.grey[300], borderRadius: BorderRadius.circular(2)))),
            const SizedBox(height: 16),
            Text(_isEdit ? 'Edit Tenant' : 'Add Tenant',
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 20),
            _field(_name, 'Full Name', Icons.person, required: true),
            const SizedBox(height: 12),
            _field(_phone, 'Phone', Icons.phone, keyboard: TextInputType.phone, required: true),
            const SizedBox(height: 12),
            _field(_email, 'Email', Icons.email, keyboard: TextInputType.emailAddress),
            const SizedBox(height: 12),
            _field(_aadhaar, 'Aadhaar Number', Icons.badge, keyboard: TextInputType.number),
            const SizedBox(height: 12),
            // Aadhaar document upload
            GestureDetector(
              onTap: () async {
                final picker = ImagePicker();
                showModalBottomSheet(
                  context: context,
                  builder: (_) => Column(mainAxisSize: MainAxisSize.min, children: [
                    ListTile(
                      leading: const Icon(Icons.camera_alt, color: AppTheme.primary),
                      title: const Text('Take Photo'),
                      onTap: () async {
                        Navigator.pop(context);
                        final img = await picker.pickImage(source: ImageSource.camera, imageQuality: 70);
                        if (img != null) setState(() { _aadhaarFile = File(img.path); _aadhaarFileName = img.name; });
                      },
                    ),
                    ListTile(
                      leading: const Icon(Icons.photo_library, color: AppTheme.primary),
                      title: const Text('Choose from Gallery'),
                      onTap: () async {
                        Navigator.pop(context);
                        final img = await picker.pickImage(source: ImageSource.gallery, imageQuality: 70);
                        if (img != null) setState(() { _aadhaarFile = File(img.path); _aadhaarFileName = img.name; });
                      },
                    ),
                  ]),
                );
              },
              child: Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: const Color(0xFFF0F2F5),
                  borderRadius: BorderRadius.circular(12),
                  border: _aadhaarFile != null
                      ? Border.all(color: AppTheme.statusPaid, width: 1.5)
                      : null,
                ),
                child: Row(children: [
                  Icon(_aadhaarFile != null ? Icons.check_circle : Icons.upload_file,
                      color: _aadhaarFile != null ? AppTheme.statusPaid : AppTheme.primary, size: 20),
                  const SizedBox(width: 10),
                  Expanded(child: Text(
                    _aadhaarFile != null ? 'Aadhaar: $_aadhaarFileName' : 'Upload Aadhaar Document (Photo/PDF)',
                    style: TextStyle(fontSize: 13,
                        color: _aadhaarFile != null ? AppTheme.statusPaid : AppTheme.textGrey),
                  )),
                  if (_aadhaarFile != null)
                    GestureDetector(
                      onTap: () => setState(() { _aadhaarFile = null; _aadhaarFileName = null; }),
                      child: const Icon(Icons.close, size: 16, color: AppTheme.textGrey),
                    ),
                ]),
              ),
            ),
            const SizedBox(height: 12),
            _field(_username, 'Username (for login)', Icons.account_circle, required: !_isEdit),
            const SizedBox(height: 12),
            _field(_password, _isEdit ? 'New Password (optional)' : 'Password', Icons.lock,
                obscure: true, required: !_isEdit),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: _saving ? null : _save,
              style: ElevatedButton.styleFrom(minimumSize: const Size(double.infinity, 50)),
              child: _saving
                  ? const CircularProgressIndicator(color: Colors.white, strokeWidth: 2)
                  : Text(_isEdit ? 'Update Tenant' : 'Add Tenant'),
            ),
          ]),
        ),
      ),
    );
  }

  Widget _field(TextEditingController ctrl, String label, IconData icon,
      {TextInputType keyboard = TextInputType.text, bool required = false, bool obscure = false}) {
    return TextFormField(
      controller: ctrl,
      keyboardType: keyboard,
      obscureText: obscure,
      decoration: InputDecoration(labelText: label, prefixIcon: Icon(icon, color: AppTheme.primary)),
      validator: required ? (v) => (v == null || v.isEmpty) ? 'Required' : null : null,
    );
  }
}
