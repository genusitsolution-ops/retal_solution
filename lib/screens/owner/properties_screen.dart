// lib/screens/owner/properties_screen.dart
import 'package:flutter/material.dart';
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

  final _dummy = [
    {
      'id': 1,
      'property_code': 'Room-101',
      'property_type': 'Room',
      'address': 'Near Bus Stand, Indore',
      'city': 'Indore',
      'base_rent': 5000,
      'status': 'occupied',
      'tenant_name': 'Ramesh Kumar'
    },
    {
      'id': 2,
      'property_code': 'Shop-01',
      'property_type': 'Shop',
      'address': 'MG Road, Raipur',
      'city': 'Raipur',
      'base_rent': 12000,
      'status': 'vacant',
      'tenant_name': null
    },
    {
      'id': 3,
      'property_code': 'Flat-2B',
      'property_type': 'Flat',
      'address': 'Vijay Nagar, Indore',
      'city': 'Indore',
      'base_rent': 9500,
      'status': 'occupied',
      'tenant_name': 'Sunita Devi'
    },
    {
      'id': 4,
      'property_code': 'Room-202',
      'property_type': 'Room',
      'address': 'Malviya Nagar, Bhopal',
      'city': 'Bhopal',
      'base_rent': 4200,
      'status': 'occupied',
      'tenant_name': 'Ajay Yadav'
    },
    {
      'id': 5,
      'property_code': 'Hall-01',
      'property_type': 'Hall',
      'address': 'Civil Lines, Raipur',
      'city': 'Raipur',
      'base_rent': 22000,
      'status': 'vacant',
      'tenant_name': null
    },
  ];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final res = await _api.get(ApiConfig.ownerProperties);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (res.success && res.data != null) {
        _properties = res.data is List ? res.data : [res.data];
      } else {
        _properties = _dummy;
        _error = res.message.isNotEmpty ? res.message : null;
      }
    });
  }

  List get _filtered {
    if (_filter == 'all') return _properties;
    return _properties
        .where((p) => p['status'] == _filter)
        .toList();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.primary,
        title: const Text('Properties',
            style: TextStyle(color: Colors.white)),
        actions: [
          IconButton(
            icon: const Icon(Icons.search, color: Colors.white),
            onPressed: () {},
          ),
        ],
        flexibleSpace: Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              colors: [AppTheme.primary, AppTheme.primaryLight],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
          ),
        ),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(48),
          child: Container(
            color: AppTheme.primary,
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
            child: Row(
              children: [
                _FilterChip(
                    label: 'All',
                    selected: _filter == 'all',
                    onTap: () => setState(() => _filter = 'all')),
                const SizedBox(width: 8),
                _FilterChip(
                    label: 'Occupied',
                    selected: _filter == 'occupied',
                    onTap: () => setState(() => _filter = 'occupied')),
                const SizedBox(width: 8),
                _FilterChip(
                    label: 'Vacant',
                    selected: _filter == 'vacant',
                    onTap: () => setState(() => _filter = 'vacant')),
              ],
            ),
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showAddDialog(),
        backgroundColor: AppTheme.primary,
        icon: const Icon(Icons.add, color: Colors.white),
        label: const Text('Add Property',
            style: TextStyle(color: Colors.white)),
      ),
      body: _loading
          ? const Center(
              child: CircularProgressIndicator(color: AppTheme.primary))
          : _filtered.isEmpty
              ? const EmptyState(
                  icon: Icons.apartment_outlined,
                  title: 'No Properties Found',
                  subtitle: 'Add your first property to get started',
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView.builder(
                    padding: const EdgeInsets.all(16),
                    itemCount: _filtered.length,
                    itemBuilder: (_, i) =>
                        _PropertyCard(property: _filtered[i]),
                  ),
                ),
    );
  }

  void _showAddDialog() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const _AddPropertySheet(),
    );
  }
}

class _PropertyCard extends StatelessWidget {
  final Map property;

  const _PropertyCard({required this.property});

  @override
  Widget build(BuildContext context) {
    final isOccupied = property['status'] == 'occupied';
    final rent = double.tryParse(property['base_rent'].toString()) ?? 0.0;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withOpacity(0.06),
              blurRadius: 10,
              offset: const Offset(0, 3))
        ],
      ),
      child: Column(
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Container(
                  width: 50,
                  height: 50,
                  decoration: BoxDecoration(
                    color: AppTheme.primary.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Center(
                    child: Text(
                      _typeIcon(property['property_type']),
                      style: const TextStyle(fontSize: 22),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment:
                            MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            property['property_code'] ?? 'Property',
                            style: const TextStyle(
                                fontSize: 15,
                                fontWeight: FontWeight.bold,
                                color: AppTheme.textDark),
                          ),
                          StatusBadge(
                              status: property['status'] ?? 'vacant'),
                        ],
                      ),
                      const SizedBox(height: 3),
                      Row(
                        children: [
                          const Icon(Icons.location_on,
                              size: 12, color: AppTheme.textGrey),
                          const SizedBox(width: 3),
                          Expanded(
                            child: Text(
                              '${property['address'] ?? ''}, ${property['city'] ?? ''}',
                              style: const TextStyle(
                                  fontSize: 12,
                                  color: AppTheme.textGrey),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const Divider(height: 1, color: AppTheme.divider),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Monthly Rent',
                        style: TextStyle(
                            fontSize: 11, color: AppTheme.textGrey)),
                    Text(
                      '₹${rent.toStringAsFixed(0)}',
                      style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: AppTheme.primary),
                    ),
                  ],
                ),
                if (isOccupied && property['tenant_name'] != null)
                  Row(
                    children: [
                      const Icon(Icons.person, size: 14, color: AppTheme.textGrey),
                      const SizedBox(width: 4),
                      Text(
                        property['tenant_name'],
                        style: const TextStyle(
                            fontSize: 12, color: AppTheme.textGrey),
                      ),
                    ],
                  ),
                Row(
                  children: [
                    IconButton(
                      icon: const Icon(Icons.edit_outlined,
                          size: 18, color: AppTheme.primary),
                      onPressed: () {},
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(),
                    ),
                    const SizedBox(width: 12),
                    IconButton(
                      icon: const Icon(Icons.delete_outline,
                          size: 18, color: AppTheme.accent),
                      onPressed: () {},
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _typeIcon(String? type) {
    switch ((type ?? '').toLowerCase()) {
      case 'shop':
        return '🏪';
      case 'flat':
        return '🏢';
      case 'hall':
        return '🏛️';
      case 'office':
        return '🏬';
      default:
        return '🏠';
    }
  }
}

class _FilterChip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _FilterChip(
      {required this.label,
      required this.selected,
      required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding:
            const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
        decoration: BoxDecoration(
          color: selected
              ? Colors.white
              : Colors.white.withOpacity(0.2),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: selected ? AppTheme.primary : Colors.white,
          ),
        ),
      ),
    );
  }
}

class _AddPropertySheet extends StatefulWidget {
  const _AddPropertySheet();

  @override
  State<_AddPropertySheet> createState() => _AddPropertySheetState();
}

class _AddPropertySheetState extends State<_AddPropertySheet> {
  final _formKey = GlobalKey<FormState>();
  final _codeCtrl = TextEditingController();
  final _addressCtrl = TextEditingController();
  final _cityCtrl = TextEditingController();
  final _rentCtrl = TextEditingController();
  final _depositCtrl = TextEditingController();
  bool _saving = false;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: EdgeInsets.fromLTRB(
          20, 20, 20, MediaQuery.of(context).viewInsets.bottom + 20),
      child: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey[300],
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              const Text('Add Property',
                  style: TextStyle(
                      fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: 20),
              TextFormField(
                controller: _codeCtrl,
                decoration: const InputDecoration(
                    labelText: 'Property Code',
                    prefixIcon:
                        Icon(Icons.tag, color: AppTheme.primary)),
                validator: (v) =>
                    v!.isEmpty ? 'Required' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _addressCtrl,
                decoration: const InputDecoration(
                    labelText: 'Address',
                    prefixIcon: Icon(Icons.location_on_outlined,
                        color: AppTheme.primary)),
                validator: (v) =>
                    v!.isEmpty ? 'Required' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _cityCtrl,
                decoration: const InputDecoration(
                    labelText: 'City',
                    prefixIcon: Icon(Icons.location_city,
                        color: AppTheme.primary)),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: TextFormField(
                      controller: _rentCtrl,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(
                          labelText: 'Base Rent (₹)',
                          prefixIcon: Icon(Icons.currency_rupee,
                              color: AppTheme.primary)),
                      validator: (v) =>
                          v!.isEmpty ? 'Required' : null,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextFormField(
                      controller: _depositCtrl,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(
                          labelText: 'Deposit (₹)',
                          prefixIcon: Icon(Icons.savings,
                              color: AppTheme.primary)),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: _saving ? null : _save,
                style: ElevatedButton.styleFrom(
                    minimumSize: const Size(double.infinity, 50)),
                child: _saving
                    ? const CircularProgressIndicator(
                        color: Colors.white, strokeWidth: 2)
                    : const Text('Add Property'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    final api = ApiService();
    final res = await api.post(ApiConfig.ownerProperties, {
      'property_code': _codeCtrl.text,
      'address': _addressCtrl.text,
      'city': _cityCtrl.text,
      'base_rent': _rentCtrl.text,
      'deposit_amount': _depositCtrl.text,
      'property_type_id': 1,
      'state': 'Madhya Pradesh',
    });
    if (!mounted) return;
    setState(() => _saving = false);
    if (res.success) {
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Property added successfully!'),
          backgroundColor: AppTheme.statusPaid,
        ),
      );
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(res.message),
          backgroundColor: AppTheme.accent,
        ),
      );
    }
  }
}
