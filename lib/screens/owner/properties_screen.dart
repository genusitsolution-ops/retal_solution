// lib/screens/owner/properties_screen.dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class PropertiesScreen extends StatefulWidget {
  const PropertiesScreen({super.key});
  @override
  State<PropertiesScreen> createState() => _PropertiesScreenState();
}

class _PropertiesScreenState extends State<PropertiesScreen> {
  final ApiService _api = ApiService();
  bool _loading = true;
  String? _error;
  List _properties = [];
  String _filter = 'all';

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    final res = await _api.get(ApiConfig.ownerProperties);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (res.success && res.data != null) {
        _properties = res.data is List ? res.data : (res.data['properties'] ?? [res.data]);
        _error = null;
      } else {
        _error = res.message.isNotEmpty ? res.message : 'Failed to load properties';
      }
    });
  }

  List get _filtered {
    if (_filter == 'all') return _properties;
    return _properties.where((p) => p['status'] == _filter).toList();
  }

  Future<void> _delete(int id) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Delete Property?'),
        content: const Text('This will permanently delete the property.'),
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
    final res = await _api.delete('${ApiConfig.ownerProperties}?id=$id');
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(res.success ? 'Property deleted!' : res.message),
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
        title: const Text('Properties', style: TextStyle(color: Colors.white)),
        flexibleSpace: Container(decoration: const BoxDecoration(
          gradient: LinearGradient(colors: [AppTheme.primary, AppTheme.primaryLight],
              begin: Alignment.topLeft, end: Alignment.bottomRight),
        )),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(44),
          child: Container(
            color: AppTheme.primary,
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
            child: Row(children: [
              _Chip('All', _filter == 'all', () => setState(() => _filter = 'all')),
              const SizedBox(width: 8),
              _Chip('Occupied', _filter == 'occupied', () => setState(() => _filter = 'occupied')),
              const SizedBox(width: 8),
              _Chip('Vacant', _filter == 'vacant', () => setState(() => _filter = 'vacant')),
            ]),
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showForm(),
        backgroundColor: AppTheme.primary,
        icon: const Icon(Icons.add, color: Colors.white),
        label: const Text('Add Property', style: TextStyle(color: Colors.white)),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: AppTheme.primary))
          : _error != null
              ? ErrorView(message: _error!, onRetry: _load)
              : _filtered.isEmpty
                  ? EmptyState(
                      icon: Icons.apartment_outlined,
                      title: 'No Properties Found',
                      subtitle: _filter != 'all' ? 'No $_filter properties' : 'Add your first property',
                      actionLabel: 'Add Property',
                      onAction: () => _showForm(),
                    )
                  : RefreshIndicator(
                      onRefresh: _load,
                      child: ListView.builder(
                        padding: const EdgeInsets.fromLTRB(16, 16, 16, 80),
                        itemCount: _filtered.length,
                        itemBuilder: (_, i) => _PropertyCard(
                          property: _filtered[i],
                          onEdit: () => _showForm(property: _filtered[i]),
                          onDelete: () => _delete(int.tryParse(_filtered[i]['id'].toString()) ?? 0),
                        ),
                      ),
                    ),
    );
  }

  void _showForm({Map? property}) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _PropertyForm(
        property: property,
        onSaved: _load,
      ),
    );
  }
}

class _PropertyCard extends StatelessWidget {
  final Map property;
  final VoidCallback onEdit;
  final VoidCallback onDelete;
  const _PropertyCard({required this.property, required this.onEdit, required this.onDelete});

  String _icon(String? t) {
    switch ((t ?? '').toLowerCase()) {
      case 'shop': return '🏪';
      case 'flat': return '🏢';
      case 'hall': return '🏛️';
      case 'office': return '🏬';
      default: return '🏠';
    }
  }

  @override
  Widget build(BuildContext context) {
    final isOccupied = property['status'] == 'occupied';
    final rent = double.tryParse(property['base_rent']?.toString() ?? '0') ?? 0;
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.06), blurRadius: 10, offset: const Offset(0, 3))],
      ),
      child: Column(children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Row(children: [
            Container(
              width: 50, height: 50,
              decoration: BoxDecoration(color: AppTheme.primary.withOpacity(0.1), borderRadius: BorderRadius.circular(12)),
              child: Center(child: Text(_icon(property['property_type']), style: const TextStyle(fontSize: 22))),
            ),
            const SizedBox(width: 12),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                Text(property['property_code'] ?? 'Property',
                    style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppTheme.textDark)),
                StatusBadge(status: property['status'] ?? 'vacant'),
              ]),
              const SizedBox(height: 3),
              Row(children: [
                const Icon(Icons.location_on, size: 12, color: AppTheme.textGrey),
                const SizedBox(width: 3),
                Expanded(child: Text(
                  '${property['address'] ?? ''}, ${property['city'] ?? ''}',
                  style: const TextStyle(fontSize: 12, color: AppTheme.textGrey),
                  overflow: TextOverflow.ellipsis,
                )),
              ]),
            ])),
          ]),
        ),
        const Divider(height: 1, color: AppTheme.divider),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
          child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('Monthly Rent', style: TextStyle(fontSize: 11, color: AppTheme.textGrey)),
              Text('₹${NumberFormat('#,##,###').format(rent)}',
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.primary)),
            ]),
            if (isOccupied && property['tenant_name'] != null)
              Row(children: [
                const Icon(Icons.person, size: 14, color: AppTheme.textGrey),
                const SizedBox(width: 4),
                Text(property['tenant_name'], style: const TextStyle(fontSize: 12, color: AppTheme.textGrey)),
              ]),
            Row(children: [
              IconButton(icon: const Icon(Icons.edit_outlined, size: 20, color: AppTheme.primary),
                  onPressed: onEdit, padding: EdgeInsets.zero, constraints: const BoxConstraints()),
              const SizedBox(width: 12),
              IconButton(icon: const Icon(Icons.delete_outline, size: 20, color: AppTheme.accent),
                  onPressed: onDelete, padding: EdgeInsets.zero, constraints: const BoxConstraints()),
            ]),
          ]),
        ),
      ]),
    );
  }
}

class _PropertyForm extends StatefulWidget {
  final Map? property;
  final VoidCallback onSaved;
  const _PropertyForm({this.property, required this.onSaved});
  @override
  State<_PropertyForm> createState() => _PropertyFormState();
}

class _PropertyFormState extends State<_PropertyForm> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _code, _address, _city, _state, _pincode, _rent, _deposit;
  String _type = '1';
  bool _saving = false;

  bool get _isEdit => widget.property != null;

  @override
  void initState() {
    super.initState();
    final p = widget.property;
    _code = TextEditingController(text: p?['property_code'] ?? '');
    _address = TextEditingController(text: p?['address'] ?? '');
    _city = TextEditingController(text: p?['city'] ?? '');
    _state = TextEditingController(text: p?['state'] ?? 'Madhya Pradesh');
    _pincode = TextEditingController(text: p?['pincode'] ?? '');
    _rent = TextEditingController(text: p?['base_rent']?.toString() ?? '');
    _deposit = TextEditingController(text: p?['deposit_amount']?.toString() ?? '');
    _type = p?['property_type_id']?.toString() ?? '1';
  }

  @override
  void dispose() {
    _code.dispose(); _address.dispose(); _city.dispose();
    _state.dispose(); _pincode.dispose(); _rent.dispose(); _deposit.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    final api = ApiService();
    final body = {
      'property_code': _code.text.trim(),
      'property_type_id': _type,
      'address': _address.text.trim(),
      'city': _city.text.trim(),
      'state': _state.text.trim(),
      'pincode': _pincode.text.trim(),
      'base_rent': _rent.text.trim(),
      'deposit_amount': _deposit.text.trim(),
    };
    ApiResponse res;
    if (_isEdit) {
      final id = widget.property!['id'];
      res = await api.put('${ApiConfig.ownerProperties}?id=$id', body);
    } else {
      res = await api.post(ApiConfig.ownerProperties, body);
    }
    if (!mounted) return;
    setState(() => _saving = false);
    Navigator.pop(context);
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(res.success ? (_isEdit ? 'Property updated!' : 'Property added!') : res.message),
      backgroundColor: res.success ? AppTheme.statusPaid : AppTheme.accent,
    ));
    if (res.success) widget.onSaved();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: EdgeInsets.fromLTRB(20, 20, 20, MediaQuery.of(context).viewInsets.bottom + 20),
      child: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
            Center(child: Container(width: 40, height: 4,
                decoration: BoxDecoration(color: Colors.grey[300], borderRadius: BorderRadius.circular(2)))),
            const SizedBox(height: 16),
            Text(_isEdit ? 'Edit Property' : 'Add Property',
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 20),
            _field(_code, 'Property Code', Icons.tag, required: true),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              value: _type,
              decoration: const InputDecoration(
                labelText: 'Property Type',
                prefixIcon: Icon(Icons.category, color: AppTheme.primary),
              ),
              items: const [
                DropdownMenuItem(value: '1', child: Text('Room')),
                DropdownMenuItem(value: '2', child: Text('Flat')),
                DropdownMenuItem(value: '3', child: Text('Shop')),
                DropdownMenuItem(value: '4', child: Text('Office')),
                DropdownMenuItem(value: '5', child: Text('Hall')),
              ],
              onChanged: (v) => setState(() => _type = v!),
            ),
            const SizedBox(height: 12),
            _field(_address, 'Address', Icons.location_on_outlined, required: true),
            const SizedBox(height: 12),
            Row(children: [
              Expanded(child: _field(_city, 'City', Icons.location_city, required: true)),
              const SizedBox(width: 12),
              Expanded(child: _field(_pincode, 'Pincode', Icons.pin_drop)),
            ]),
            const SizedBox(height: 12),
            _field(_state, 'State', Icons.map),
            const SizedBox(height: 12),
            Row(children: [
              Expanded(child: _field(_rent, 'Base Rent (₹)', Icons.currency_rupee,
                  keyboard: TextInputType.number, required: true)),
              const SizedBox(width: 12),
              Expanded(child: _field(_deposit, 'Deposit (₹)', Icons.savings,
                  keyboard: TextInputType.number)),
            ]),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: _saving ? null : _save,
              style: ElevatedButton.styleFrom(minimumSize: const Size(double.infinity, 50)),
              child: _saving
                  ? const CircularProgressIndicator(color: Colors.white, strokeWidth: 2)
                  : Text(_isEdit ? 'Update Property' : 'Add Property'),
            ),
          ]),
        ),
      ),
    );
  }

  Widget _field(TextEditingController ctrl, String label, IconData icon,
      {TextInputType keyboard = TextInputType.text, bool required = false}) {
    return TextFormField(
      controller: ctrl,
      keyboardType: keyboard,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon, color: AppTheme.primary),
      ),
      validator: required ? (v) => (v == null || v.isEmpty) ? 'Required' : null : null,
    );
  }
}

class _Chip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;
  const _Chip(this.label, this.selected, this.onTap);
  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
        decoration: BoxDecoration(
          color: selected ? Colors.white : Colors.white.withOpacity(0.2),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(label, style: TextStyle(
          fontSize: 12, fontWeight: FontWeight.w600,
          color: selected ? AppTheme.primary : Colors.white,
        )),
      ),
    );
  }
}
