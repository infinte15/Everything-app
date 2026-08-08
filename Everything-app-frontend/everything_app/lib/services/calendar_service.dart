import 'package:flutter/foundation.dart';
import '../config/api_config.dart';
import '../models/calendar_event.dart';
import 'api_service.dart';

class CalendarService {
  final ApiService _apiService = ApiService();

  Future<List<CalendarEvent>> getEventsInRange(
    DateTime startDate,
    DateTime endDate,
  ) async {
    try {
      final url = '${ApiConfig.calendarEvents}?'
          'startDate=${startDate.toIso8601String()}&'
          'endDate=${endDate.toIso8601String()}';

      final response = await _apiService.get(url);

      if (_apiService.isSuccess(response)) {
        final List<dynamic> data = _apiService.parseResponse(response);
        return data.map((json) => CalendarEvent.fromJson(json)).toList();
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error fetching calendar events: $e');
      return [];
    }
  }

  Future<List<CalendarEvent>> getEventsForDate(DateTime date) async {
    final startOfDay = DateTime(date.year, date.month, date.day);
    final endOfDay = DateTime(date.year, date.month, date.day, 23, 59, 59);
    
    return await getEventsInRange(startOfDay, endOfDay);
  }

  Future<CalendarEvent?> createEvent(CalendarEvent event) async {
    debugPrint('📤 [CalendarService] createEvent POST ${ApiConfig.calendarEvents}');
    try {
      final response = await _apiService.post(
        ApiConfig.calendarEvents,
        event.toJson(),
      );
      
      debugPrint('📥 [CalendarService] createEvent Status: ${response.statusCode}');
      if (_apiService.isSuccess(response)) {
        final data = _apiService.parseResponse(response);
        return CalendarEvent.fromJson(data);
      }
      debugPrint('❌ [CalendarService] createEvent Failed: ${response.body}');
      return null;
    } catch (e) {
      debugPrint('💥 [CalendarService] createEvent Exception: $e');
      return null;
    }
  }

  Future<CalendarEvent?> updateEvent(CalendarEvent event) async {
    try {
      if (event.id == null) {
        throw Exception('Event ID is required for update');
      }

      final response = await _apiService.put(
        ApiConfig.calendarEventById(event.id!),
        event.toJson(),
      );

      if (_apiService.isSuccess(response)) {
        final data = _apiService.parseResponse(response);
        return CalendarEvent.fromJson(data);
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error updating event: $e');
      return null;
    }
  }

  /// Pinnt ein Event fest oder gibt es frei. Eigener Endpunkt, weil updateEvent einen
  /// verschobenen TASK serverseitig bewusst anpinnt und ein isFixed=false dort sofort
  /// wieder überschrieben würde. PUT statt PATCH, weil ApiService kein patch kennt.
  Future<CalendarEvent?> setPinned(int id, bool pinned) async {
    try {
      final response = await _apiService.put(
        ApiConfig.calendarEventPin(id),
        {'pinned': pinned},
      );

      if (_apiService.isSuccess(response)) {
        return CalendarEvent.fromJson(_apiService.parseResponse(response));
      }
      throw Exception(_apiService.getErrorMessage(response));
    } catch (e) {
      debugPrint('Error pinning event: $e');
      return null;
    }
  }

  /// Überspringt eine Ausführung oder holt sie zurück.
  ///
  /// Der Gegenentwurf zum Löschen: bei Gewohnheiten, Projektzeit und Trainings war Löschen
  /// wirkungslos, weil die Woche danach unter ihrem Pensum stand und der Scheduler binnen
  /// Sekunden Ersatz anlegte. Übersprungen bleibt der Block stehen, gibt aber seine Zeit frei.
  Future<CalendarEvent?> setSkipped(int id, bool skipped) async {
    try {
      final response = await _apiService.put(
        ApiConfig.calendarEventSkip(id),
        {'skipped': skipped},
      );

      if (_apiService.isSuccess(response)) {
        return CalendarEvent.fromJson(_apiService.parseResponse(response));
      }
      throw Exception(_apiService.getErrorMessage(response));
    } catch (e) {
      debugPrint('Error skipping event: $e');
      return null;
    }
  }

  /// Hakt einen Aufgabenblock ab oder nimmt das zurück.
  ///
  /// Eigener Endpunkt, weil das Abhaken Minuten gutschreibt — ans Lernziel oder an die
  /// Aufgabe. Über ein gewöhnliches Update dürfte das nicht auslösbar sein.
  Future<CalendarEvent?> setCompleted(int id, bool completed) async {
    try {
      final response = await _apiService.put(
        ApiConfig.calendarEventComplete(id),
        {'completed': completed},
      );

      if (_apiService.isSuccess(response)) {
        return CalendarEvent.fromJson(_apiService.parseResponse(response));
      }
      throw Exception(_apiService.getErrorMessage(response));
    } catch (e) {
      debugPrint('Error completing event: $e');
      return null;
    }
  }

  Future<bool> deleteEvent(int id) async {
    try {
      final response = await _apiService.delete(
        ApiConfig.calendarEventById(id),
      );
      return _apiService.isSuccess(response);
    } catch (e) {
      debugPrint('Error deleting event: $e');
      return false;
    }
  }


  // endDate ist optional: ohne Enddatum plant das Backend über seinen konfigurierten Horizont
  // (scheduler.horizon-days). Der Client soll die Horizontlänge nicht kennen müssen — ein hier
  // hartkodiertes Fenster deckte sonst weniger Wochen ab als die automatische Neuplanung.
  Future<Map<String, dynamic>> generateSchedule(
    DateTime startDate, {
    DateTime? endDate,
  }) async {
    try {
      final response = await _apiService.post(
        ApiConfig.generateSchedule,
        {
          'startDate': startDate.toIso8601String().split('T')[0],
          if (endDate != null) 'endDate': endDate.toIso8601String().split('T')[0],
        },
      );

      if (_apiService.isSuccess(response)) {
        return {
          'success': true,
          'data': _apiService.parseResponse(response),
        };
      } else {
        return {
          'success': false,
          'error': _apiService.getErrorMessage(response),
        };
      }
    } catch (e) {
      debugPrint('Error generating schedule: $e');
      return {
        'success': false,
        'error': 'Verbindungsfehler: $e',
      };
    }
  }
}