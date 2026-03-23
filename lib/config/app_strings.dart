// lib/config/app_strings.dart
// Multi-language support: English, Hindi, Marathi

enum AppLanguage { english, hindi, marathi }

class AppStrings {
  static AppLanguage _current = AppLanguage.english;

  static AppLanguage get current => _current;
  static void setLanguage(AppLanguage lang) => _current = lang;

  static String get(String key) {
    final map = _strings[key];
    if (map == null) return key;
    return map[_current] ?? map[AppLanguage.english] ?? key;
  }

  // Language names for display
  static const Map<AppLanguage, String> languageNames = {
    AppLanguage.english: 'English',
    AppLanguage.hindi: 'हिंदी',
    AppLanguage.marathi: 'मराठी',
  };

  static const Map<AppLanguage, String> languageFlags = {
    AppLanguage.english: '🇬🇧',
    AppLanguage.hindi: '🇮🇳',
    AppLanguage.marathi: '🏵️',
  };

  static const Map<String, Map<AppLanguage, String>> _strings = {
    // App
    'app_name': {
      AppLanguage.english: 'PRMS',
      AppLanguage.hindi: 'PRMS',
      AppLanguage.marathi: 'PRMS',
    },
    'property_rental_management': {
      AppLanguage.english: 'Property Rental Management',
      AppLanguage.hindi: 'संपत्ति किराया प्रबंधन',
      AppLanguage.marathi: 'मालमत्ता भाडे व्यवस्थापन',
    },

    // Login
    'welcome_back': {
      AppLanguage.english: 'Welcome back 👋',
      AppLanguage.hindi: 'वापसी पर स्वागत है 👋',
      AppLanguage.marathi: 'पुन्हा स्वागत आहे 👋',
    },
    'sign_in_continue': {
      AppLanguage.english: 'Sign in to continue',
      AppLanguage.hindi: 'जारी रखने के लिए साइन इन करें',
      AppLanguage.marathi: 'पुढे जाण्यासाठी साइन इन करा',
    },
    'username': {
      AppLanguage.english: 'Username',
      AppLanguage.hindi: 'उपयोगकर्ता नाम',
      AppLanguage.marathi: 'वापरकर्तानाव',
    },
    'password': {
      AppLanguage.english: 'Password',
      AppLanguage.hindi: 'पासवर्ड',
      AppLanguage.marathi: 'पासवर्ड',
    },
    'sign_in': {
      AppLanguage.english: 'Sign In',
      AppLanguage.hindi: 'साइन इन करें',
      AppLanguage.marathi: 'साइन इन करा',
    },
    'role_auto': {
      AppLanguage.english: 'Role is detected automatically from your account',
      AppLanguage.hindi: 'भूमिका स्वचालित रूप से पता चल जाती है',
      AppLanguage.marathi: 'भूमिका आपोआप शोधली जाते',
    },
    'select_language': {
      AppLanguage.english: 'Select Language',
      AppLanguage.hindi: 'भाषा चुनें',
      AppLanguage.marathi: 'भाषा निवडा',
    },

    // Navigation
    'dashboard': {
      AppLanguage.english: 'Dashboard',
      AppLanguage.hindi: 'डैशबोर्ड',
      AppLanguage.marathi: 'डॅशबोर्ड',
    },
    'properties': {
      AppLanguage.english: 'Properties',
      AppLanguage.hindi: 'संपत्तियां',
      AppLanguage.marathi: 'मालमत्ता',
    },
    'tenants': {
      AppLanguage.english: 'Tenants',
      AppLanguage.hindi: 'किरायेदार',
      AppLanguage.marathi: 'भाडेकरू',
    },
    'agents': {
      AppLanguage.english: 'Agents',
      AppLanguage.hindi: 'एजेंट',
      AppLanguage.marathi: 'एजंट',
    },
    'profile': {
      AppLanguage.english: 'Profile',
      AppLanguage.hindi: 'प्रोफ़ाइल',
      AppLanguage.marathi: 'प्रोफाइल',
    },
    'collections': {
      AppLanguage.english: 'Collections',
      AppLanguage.hindi: 'संग्रह',
      AppLanguage.marathi: 'संकलन',
    },
    'invoices': {
      AppLanguage.english: 'Invoices',
      AppLanguage.hindi: 'चालान',
      AppLanguage.marathi: 'बीजक',
    },
    'reports': {
      AppLanguage.english: 'Reports',
      AppLanguage.hindi: 'रिपोर्ट',
      AppLanguage.marathi: 'अहवाल',
    },
    'allocations': {
      AppLanguage.english: 'Allocations',
      AppLanguage.hindi: 'आवंटन',
      AppLanguage.marathi: 'वाटप',
    },

    // Dashboard
    'good_morning': {
      AppLanguage.english: 'Good Morning',
      AppLanguage.hindi: 'सुप्रभात',
      AppLanguage.marathi: 'शुभ सकाळ',
    },
    'good_afternoon': {
      AppLanguage.english: 'Good Afternoon',
      AppLanguage.hindi: 'शुभ दोपहर',
      AppLanguage.marathi: 'शुभ दुपार',
    },
    'good_evening': {
      AppLanguage.english: 'Good Evening',
      AppLanguage.hindi: 'शुभ संध्या',
      AppLanguage.marathi: 'शुभ संध्याकाळ',
    },
    'monthly_revenue': {
      AppLanguage.english: 'Monthly Revenue',
      AppLanguage.hindi: 'मासिक आय',
      AppLanguage.marathi: 'मासिक उत्पन्न',
    },
    'total_properties': {
      AppLanguage.english: 'Properties',
      AppLanguage.hindi: 'संपत्तियां',
      AppLanguage.marathi: 'मालमत्ता',
    },
    'active_tenants': {
      AppLanguage.english: 'Active Tenants',
      AppLanguage.hindi: 'सक्रिय किरायेदार',
      AppLanguage.marathi: 'सक्रिय भाडेकरू',
    },
    'pending_dues': {
      AppLanguage.english: 'Pending Dues',
      AppLanguage.hindi: 'बकाया',
      AppLanguage.marathi: 'थकबाकी',
    },
    'quick_actions': {
      AppLanguage.english: 'Quick Actions',
      AppLanguage.hindi: 'त्वरित क्रियाएं',
      AppLanguage.marathi: 'जलद क्रिया',
    },
    'recent_invoices': {
      AppLanguage.english: 'Recent Invoices',
      AppLanguage.hindi: 'हाल के चालान',
      AppLanguage.marathi: 'अलीकडील बीजके',
    },
    'view_all': {
      AppLanguage.english: 'View All',
      AppLanguage.hindi: 'सभी देखें',
      AppLanguage.marathi: 'सर्व पहा',
    },
    'collected': {
      AppLanguage.english: 'Collected',
      AppLanguage.hindi: 'एकत्र',
      AppLanguage.marathi: 'जमा',
    },

    // Properties
    'add_property': {
      AppLanguage.english: 'Add Property',
      AppLanguage.hindi: 'संपत्ति जोड़ें',
      AppLanguage.marathi: 'मालमत्ता जोडा',
    },
    'edit_property': {
      AppLanguage.english: 'Edit Property',
      AppLanguage.hindi: 'संपत्ति संपादित करें',
      AppLanguage.marathi: 'मालमत्ता संपादित करा',
    },
    'property_code': {
      AppLanguage.english: 'Property Code',
      AppLanguage.hindi: 'संपत्ति कोड',
      AppLanguage.marathi: 'मालमत्ता कोड',
    },
    'property_type': {
      AppLanguage.english: 'Property Type',
      AppLanguage.hindi: 'संपत्ति प्रकार',
      AppLanguage.marathi: 'मालमत्ता प्रकार',
    },
    'address': {
      AppLanguage.english: 'Address',
      AppLanguage.hindi: 'पता',
      AppLanguage.marathi: 'पत्ता',
    },
    'city': {
      AppLanguage.english: 'City',
      AppLanguage.hindi: 'शहर',
      AppLanguage.marathi: 'शहर',
    },
    'base_rent': {
      AppLanguage.english: 'Base Rent (₹)',
      AppLanguage.hindi: 'मूल किराया (₹)',
      AppLanguage.marathi: 'मूळ भाडे (₹)',
    },
    'deposit': {
      AppLanguage.english: 'Deposit (₹)',
      AppLanguage.hindi: 'जमा (₹)',
      AppLanguage.marathi: 'ठेव (₹)',
    },
    'occupied': {
      AppLanguage.english: 'Occupied',
      AppLanguage.hindi: 'अधिगृहीत',
      AppLanguage.marathi: 'व्यापलेले',
    },
    'vacant': {
      AppLanguage.english: 'Vacant',
      AppLanguage.hindi: 'खाली',
      AppLanguage.marathi: 'रिकामे',
    },
    'all': {
      AppLanguage.english: 'All',
      AppLanguage.hindi: 'सभी',
      AppLanguage.marathi: 'सर्व',
    },

    // Tenants
    'add_tenant': {
      AppLanguage.english: 'Add Tenant',
      AppLanguage.hindi: 'किरायेदार जोड़ें',
      AppLanguage.marathi: 'भाडेकरू जोडा',
    },
    'full_name': {
      AppLanguage.english: 'Full Name',
      AppLanguage.hindi: 'पूरा नाम',
      AppLanguage.marathi: 'पूर्ण नाव',
    },
    'phone': {
      AppLanguage.english: 'Phone',
      AppLanguage.hindi: 'फोन',
      AppLanguage.marathi: 'फोन',
    },
    'email': {
      AppLanguage.english: 'Email',
      AppLanguage.hindi: 'ईमेल',
      AppLanguage.marathi: 'ईमेल',
    },
    'aadhaar': {
      AppLanguage.english: 'Aadhaar Number',
      AppLanguage.hindi: 'आधार नंबर',
      AppLanguage.marathi: 'आधार क्रमांक',
    },

    // Status
    'paid': {
      AppLanguage.english: 'Paid',
      AppLanguage.hindi: 'भुगतान',
      AppLanguage.marathi: 'भरलेले',
    },
    'pending': {
      AppLanguage.english: 'Pending',
      AppLanguage.hindi: 'लंबित',
      AppLanguage.marathi: 'प्रलंबित',
    },
    'overdue': {
      AppLanguage.english: 'Overdue',
      AppLanguage.hindi: 'अतिदेय',
      AppLanguage.marathi: 'थकीत',
    },
    'active': {
      AppLanguage.english: 'Active',
      AppLanguage.hindi: 'सक्रिय',
      AppLanguage.marathi: 'सक्रिय',
    },

    // Actions
    'save': {
      AppLanguage.english: 'Save',
      AppLanguage.hindi: 'सहेजें',
      AppLanguage.marathi: 'जतन करा',
    },
    'cancel': {
      AppLanguage.english: 'Cancel',
      AppLanguage.hindi: 'रद्द करें',
      AppLanguage.marathi: 'रद्द करा',
    },
    'delete': {
      AppLanguage.english: 'Delete',
      AppLanguage.hindi: 'हटाएं',
      AppLanguage.marathi: 'हटवा',
    },
    'edit': {
      AppLanguage.english: 'Edit',
      AppLanguage.hindi: 'संपादित',
      AppLanguage.marathi: 'संपादित',
    },
    'retry': {
      AppLanguage.english: 'Retry',
      AppLanguage.hindi: 'पुनः प्रयास',
      AppLanguage.marathi: 'पुन्हा प्रयत्न करा',
    },
    'logout': {
      AppLanguage.english: 'Logout',
      AppLanguage.hindi: 'लॉग आउट',
      AppLanguage.marathi: 'लॉग आउट',
    },
    'logout_confirm': {
      AppLanguage.english: 'Are you sure you want to logout?',
      AppLanguage.hindi: 'क्या आप लॉग आउट करना चाहते हैं?',
      AppLanguage.marathi: 'तुम्हाला लॉग आउट करायचे आहे का?',
    },

    // Collections
    'record_collection': {
      AppLanguage.english: 'Record Collection',
      AppLanguage.hindi: 'भुगतान रिकॉर्ड करें',
      AppLanguage.marathi: 'पेमेंट नोंद करा',
    },
    'select_tenant': {
      AppLanguage.english: 'Select Tenant',
      AppLanguage.hindi: 'किरायेदार चुनें',
      AppLanguage.marathi: 'भाडेकरू निवडा',
    },
    'amount': {
      AppLanguage.english: 'Amount (₹)',
      AppLanguage.hindi: 'राशि (₹)',
      AppLanguage.marathi: 'रक्कम (₹)',
    },
    'payment_mode': {
      AppLanguage.english: 'Payment Mode',
      AppLanguage.hindi: 'भुगतान विधि',
      AppLanguage.marathi: 'पेमेंट पद्धत',
    },
    'remarks': {
      AppLanguage.english: 'Remarks (optional)',
      AppLanguage.hindi: 'टिप्पणी (वैकल्पिक)',
      AppLanguage.marathi: 'शेरा (पर्यायी)',
    },
    'submit_collection': {
      AppLanguage.english: 'Submit Collection',
      AppLanguage.hindi: 'भुगतान सबमिट करें',
      AppLanguage.marathi: 'पेमेंट सबमिट करा',
    },
    'send_receipt': {
      AppLanguage.english: 'Send Receipt',
      AppLanguage.hindi: 'रसीद भेजें',
      AppLanguage.marathi: 'पावती पाठवा',
    },

    // Invoices
    'record_payment': {
      AppLanguage.english: 'Record Payment',
      AppLanguage.hindi: 'भुगतान दर्ज करें',
      AppLanguage.marathi: 'पेमेंट नोंद करा',
    },
    'total': {
      AppLanguage.english: 'Total',
      AppLanguage.hindi: 'कुल',
      AppLanguage.marathi: 'एकूण',
    },
    'due': {
      AppLanguage.english: 'Due',
      AppLanguage.hindi: 'बकाया',
      AppLanguage.marathi: 'थकबाकी',
    },
    'fully_paid': {
      AppLanguage.english: 'Fully Paid ✓',
      AppLanguage.hindi: 'पूरा भुगतान ✓',
      AppLanguage.marathi: 'पूर्ण भरलेले ✓',
    },

    // Profile
    'account_info': {
      AppLanguage.english: 'Account Info',
      AppLanguage.hindi: 'खाता जानकारी',
      AppLanguage.marathi: 'खाते माहिती',
    },
    'change_password': {
      AppLanguage.english: 'Change Password',
      AppLanguage.hindi: 'पासवर्ड बदलें',
      AppLanguage.marathi: 'पासवर्ड बदला',
    },
    'language': {
      AppLanguage.english: 'Language',
      AppLanguage.hindi: 'भाषा',
      AppLanguage.marathi: 'भाषा',
    },
    'app_version': {
      AppLanguage.english: 'App Version',
      AppLanguage.hindi: 'ऐप संस्करण',
      AppLanguage.marathi: 'ॲप आवृत्ती',
    },

    // Errors / Empty
    'no_data': {
      AppLanguage.english: 'No data found',
      AppLanguage.hindi: 'कोई डेटा नहीं मिला',
      AppLanguage.marathi: 'डेटा सापडला नाही',
    },
    'loading': {
      AppLanguage.english: 'Loading...',
      AppLanguage.hindi: 'लोड हो रहा है...',
      AppLanguage.marathi: 'लोड होत आहे...',
    },
    'required': {
      AppLanguage.english: 'Required',
      AppLanguage.hindi: 'आवश्यक',
      AppLanguage.marathi: 'आवश्यक',
    },

    // Allocation
    'new_allocation': {
      AppLanguage.english: 'New Allocation',
      AppLanguage.hindi: 'नया आवंटन',
      AppLanguage.marathi: 'नवीन वाटप',
    },
    'select_property': {
      AppLanguage.english: 'Select Property',
      AppLanguage.hindi: 'संपत्ति चुनें',
      AppLanguage.marathi: 'मालमत्ता निवडा',
    },
    'start_date': {
      AppLanguage.english: 'Start Date',
      AppLanguage.hindi: 'प्रारंभ तिथि',
      AppLanguage.marathi: 'प्रारंभ तारीख',
    },
    'close_allocation': {
      AppLanguage.english: 'Close Allocation',
      AppLanguage.hindi: 'आवंटन बंद करें',
      AppLanguage.marathi: 'वाटप बंद करा',
    },

    // Agent
    'add_agent': {
      AppLanguage.english: 'Add Agent',
      AppLanguage.hindi: 'एजेंट जोड़ें',
      AppLanguage.marathi: 'एजंट जोडा',
    },
    'assign_properties': {
      AppLanguage.english: 'Assign Properties',
      AppLanguage.hindi: 'संपत्तियां असाइन करें',
      AppLanguage.marathi: 'मालमत्ता नियुक्त करा',
    },
    'my_tenants': {
      AppLanguage.english: 'My Tenants',
      AppLanguage.hindi: 'मेरे किरायेदार',
      AppLanguage.marathi: 'माझे भाडेकरू',
    },
  };
}
