// lib/screens/owner/employees_screen.dart
import 'package:flutter/material.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class EmployeesScreen extends StatefulWidget {
  const EmployeesScreen({super.key});
  @override
  State<EmployeesScreen> createState() => _EmployeesScreenState();
}

class _EmployeesScreenState extends State<EmployeesScreen> {
  final ApiService _api = ApiService();
  bool _loading = true;
  String? _error;
  List _employees = [];

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    final res = await _api.get(ApiConfig.ownerEmployees);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (res.success && res.data != null) {
        _employees = res.data is List ? res.data : (res.data['employees'] ?? [res.data]);
      } else {
        _error = res.message.isNotEmpty ? res.message : 'Failed to load agents';
      }
    });
  }

  Future<void> _delete(dynamic id) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Delete Agent?'),
        content: const Text('This will permanently delete the agent.'),
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
    final res = await _api.delete('${ApiConfig.ownerEmployees}?id=$id');
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(res.success ? 'Agent deleted!' : res.message),
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
        title: const Text('Agents / Employees', style: TextStyle(color: Colors.white)),
        flexibleSpace: Container(decoration: const BoxDecoration(
          gradient: LinearGradient(colors: [AppTheme.primary, AppTheme.primaryLight],
              begin: Alignment.topLeft, end: Alignment.bottomRight),
        )),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showForm(),
        backgroundColor: AppTheme.primary,
        icon: const Icon(Icons.person_add, color: Colors.white),
        label: const Text('Add Agent', style: TextStyle(color: Colors.white)),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: AppTheme.primary))
          : _error != null
              ? ErrorView(message: _error!, onRetry: _load)
              : _employees.isEmpty
                  ? EmptyState(
                      icon: Icons.badge_outlined,
                      title: 'No Agents Found',
                      actionLabel: 'Add Agent',
                      onAction: () => _showForm(),
                    )
                  : RefreshIndicator(
                      onRefresh: _load,
                      child: ListView.builder(
                        padding: const EdgeInsets.fromLTRB(16, 16, 16, 80),
                        itemCount: _employees.length,
                        itemBuilder: (_, i) => _EmpCard(
                          emp: _employees[i],
                          rank: i + 1,
                          onEdit: () => _showForm(emp: _employees[i]),
                          onDelete: () => _delete(_employees[i]['id']),
                          onAssign: () => _showAssign(_employees[i]),
                        ),
                      ),
                    ),
    );
  }

  void _showForm({Map? emp}) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _EmpForm(emp: emp, onSaved: _load),
    );
  }

  void _showAssign(Map emp) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _AssignSheet(emp: emp, onSaved: _load),
    );
  }
}

class _EmpCard extends StatelessWidget {
  final Map emp;
  final int rank;
  final VoidCallback onEdit;
  final VoidCallback onDelete;
  final VoidCallback onAssign;
  const _EmpCard({required this.emp, required this.rank, required this.onEdit,
      required this.onDelete, required this.onAssign});

  @override
  Widget build(BuildContext context) {
    final initials = (emp['full_name'] ?? 'E').split(' ').take(2)
        .map((s) => s.isNotEmpty ? s[0].toUpperCase() : '').join();
    final collections = double.tryParse(emp['collections_this_month']?.toString() ?? '0') ?? 0.0;
    final colors = [AppTheme.primary, AppTheme.accentOrange, AppTheme.accentPurple];
    final color = colors[rank % colors.length];

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 10, offset: const Offset(0, 3))],
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(children: [
          Row(children: [
            CircleAvatar(
              backgroundColor: color.withOpacity(0.15), radius: 26,
              child: Text(initials, style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 16)),
            ),
            const SizedBox(width: 12),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(emp['full_name'] ?? '', style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppTheme.textDark)),
              Text(emp['phone'] ?? '', style: const TextStyle(fontSize: 12, color: AppTheme.textGrey)),
              Text(emp['email'] ?? '', style: const TextStyle(fontSize: 11, color: AppTheme.textGrey)),
            ])),
            StatusBadge(status: emp['status'] ?? 'active'),
          ]),
          const SizedBox(height: 12),
          const Divider(height: 1, color: AppTheme.divider),
          const SizedBox(height: 10),
          Row(children: [
            Expanded(child: _metric('Properties', '${emp['assigned_properties'] ?? 0}', Icons.apartment, AppTheme.primary)),
            Expanded(child: _metric('This Month', '₹${(collections/1000).toStringAsFixed(1)}K', Icons.account_balance_wallet, AppTheme.statusPaid)),
            Expanded(child: GestureDetector(
              onTap: onAssign,
              child: _metric('Assign', 'Props', Icons.assignment, AppTheme.accentOrange),
            )),
            Expanded(child: GestureDetector(
              onTap: onEdit,
              child: _metric('Edit', '', Icons.edit, AppTheme.primary),
            )),
            Expanded(child: GestureDetector(
              onTap: onDelete,
              child: _metric('Delete', '', Icons.delete, AppTheme.accent),
            )),
          ]),
        ]),
      ),
    );
  }

  Widget _metric(String label, String value, IconData icon, Color color) {
    return Column(children: [
      Icon(icon, size: 18, color: color),
      if (value.isNotEmpty) ...[
        const SizedBox(height: 3),
        Text(value, style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: color)),
      ],
      Text(label, style: const TextStyle(fontSize: 10, color: AppTheme.textGrey)),
    ]);
  }
}

class _EmpForm extends StatefulWidget {
  final Map? emp;
  final VoidCallback onSaved;
  const _EmpForm({this.emp, required this.onSaved});
  @override
  State<_EmpForm> createState() => _EmpFormState();
}

class _EmpFormState extends State<_EmpForm> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _name, _phone, _email, _username, _password;
  bool _saving = false;
  bool get _isEdit => widget.emp != null;

  @override
  void initState() {
    super.initState();
    final e = widget.emp;
    _name = TextEditingController(text: e?['full_name'] ?? '');
    _phone = TextEditingController(text: e?['phone'] ?? '');
    _email = TextEditingController(text: e?['email'] ?? '');
    _username = TextEditingController(text: e?['username'] ?? '');
    _password = TextEditingController();
  }

  @override
  void dispose() {
    _name.dispose(); _phone.dispose(); _email.dispose();
    _username.dispose(); _password.dispose();
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
      'username': _username.text.trim(),
      if (!_isEdit || _password.text.isNotEmpty) 'password': _password.text,
    };
    ApiResponse res;
    if (_isEdit) {
      res = await api.put('${ApiConfig.ownerEmployees}?id=${widget.emp!['id']}', body);
    } else {
      res = await api.post(ApiConfig.ownerEmployees, body);
    }
    if (!mounted) return;
    setState(() => _saving = false);
    Navigator.pop(context);
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(res.success ? (_isEdit ? 'Agent updated!' : 'Agent added!') : res.message),
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
            Text(_isEdit ? 'Edit Agent' : 'Add Agent',
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 20),
            _f(_name, 'Full Name', Icons.person, required: true),
            const SizedBox(height: 12),
            _f(_phone, 'Phone', Icons.phone, keyboard: TextInputType.phone, required: true),
            const SizedBox(height: 12),
            _f(_email, 'Email', Icons.email, keyboard: TextInputType.emailAddress),
            const SizedBox(height: 12),
            _f(_username, 'Username', Icons.account_circle, required: !_isEdit),
            const SizedBox(height: 12),
            _f(_password, _isEdit ? 'New Password (optional)' : 'Password',
                Icons.lock, obscure: true, required: !_isEdit),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: _saving ? null : _save,
              style: ElevatedButton.styleFrom(minimumSize: const Size(double.infinity, 50)),
              child: _saving
                  ? const CircularProgressIndicator(color: Colors.white, strokeWidth: 2)
                  : Text(_isEdit ? 'Update Agent' : 'Add Agent'),
            ),
          ]),
        ),
      ),
    );
  }

  Widget _f(TextEditingController c, String label, IconData icon,
      {TextInputType keyboard = TextInputType.text, bool required = false, bool obscure = false}) {
    return TextFormField(
      controller: c, keyboardType: keyboard, obscureText: obscure,
      decoration: InputDecoration(labelText: label, prefixIcon: Icon(icon, color: AppTheme.primary)),
      validator: required ? (v) => (v == null || v.isEmpty) ? 'Required' : null : null,
    );
  }
}

class _AssignSheet extends StatefulWidget {
  final Map emp;
  final VoidCallback onSaved;
  const _AssignSheet({required this.emp, required this.onSaved});
  @override
  State<_AssignSheet> createState() => _AssignSheetState();
}

class _AssignSheetState extends State<_AssignSheet> {
  final ApiService _api = ApiService();
  bool _loading = true;
  List _properties = [];
  List<int> _selected = [];
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _loadProps();
  }

  Future<void> _loadProps() async {
    final res = await _api.get(ApiConfig.ownerProperties);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (res.success && res.data != null) {
        _properties = res.data is List ? res.data : (res.data['properties'] ?? []);
      }
    });
  }

  Future<void> _assign() async {
    if (_selected.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Select at least one property')));
      return;
    }
    setState(() => _saving = true);
    final res = await _api.post(
      '${ApiConfig.ownerEmployees}?action=assign&id=${widget.emp['id']}',
      {'property_ids': _selected},
    );
    if (!mounted) return;
    setState(() => _saving = false);
    Navigator.pop(context);
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(res.success ? 'Properties assigned!' : res.message),
      backgroundColor: res.success ? AppTheme.statusPaid : AppTheme.accent,
    ));
    if (res.success) widget.onSaved();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      padding: const EdgeInsets.all(20),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Center(child: Container(width: 40, height: 4,
            decoration: BoxDecoration(color: Colors.grey[300], borderRadius: BorderRadius.circular(2)))),
        const SizedBox(height: 16),
        Text('Assign Properties to ${widget.emp['full_name'] ?? 'Agent'}',
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
        const SizedBox(height: 16),
        if (_loading)
          const Center(child: CircularProgressIndicator())
        else if (_properties.isEmpty)
          const Text('No properties available', style: TextStyle(color: AppTheme.textGrey))
        else
          SizedBox(
            height: 300,
            child: ListView.builder(
              itemCount: _properties.length,
              itemBuilder: (_, i) {
                final p = _properties[i];
                final id = int.tryParse(p['id'].toString()) ?? 0;
                return CheckboxListTile(
                  title: Text('${p['property_code']} - ${p['city'] ?? ''}'),
                  subtitle: Text('₹${p['base_rent'] ?? 0}/mo'),
                  value: _selected.contains(id),
                  onChanged: (v) => setState(() {
                    if (v == true) _selected.add(id);
                    else _selected.remove(id);
                  }),
                  activeColor: AppTheme.primary,
                );
              },
            ),
          ),
        const SizedBox(height: 16),
        ElevatedButton(
          onPressed: _saving ? null : _assign,
          style: ElevatedButton.styleFrom(minimumSize: const Size(double.infinity, 50)),
          child: _saving
              ? const CircularProgressIndicator(color: Colors.white, strokeWidth: 2)
              : Text('Assign ${_selected.length} Properties'),
        ),
      ]),
    );
  }
}
