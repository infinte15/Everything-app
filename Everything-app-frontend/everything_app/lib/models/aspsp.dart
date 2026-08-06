/// Ein Institut in der Bankenauswahl.
///
/// Ohne ID: identifiziert wird über Name und Land, beides muss unverändert
/// zurückgeschickt werden.
class Aspsp {
  final String name;
  final String country;
  final String? logoUrl;

  /// Verbund ("Sparkassen", "Volksbanken Raiffeisenbanken"). Die einzige
  /// Möglichkeit, mehrere hundert regionale Institute zu bündeln.
  final String? group;

  final bool beta;

  /// `false` heißt: die Bank kann den Browser-Ablauf nicht (DECOUPLED oder
  /// EMBEDDED). Ein Redirect endete dort in einem leeren Fenster.
  final bool redirectSupported;

  Aspsp({
    required this.name,
    required this.country,
    this.logoUrl,
    this.group,
    this.beta = false,
    this.redirectSupported = true,
  });

  factory Aspsp.fromJson(Map<String, dynamic> json) {
    return Aspsp(
      name: json['name'] ?? '',
      country: json['country'] ?? 'DE',
      logoUrl: json['logoUrl'],
      group: json['group'],
      beta: json['beta'] ?? false,
      redirectSupported: json['redirectSupported'] ?? false,
    );
  }
}
