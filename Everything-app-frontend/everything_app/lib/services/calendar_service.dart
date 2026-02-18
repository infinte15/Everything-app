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
      print('Error fetching calendar events: $e');
      return [];
    }
  }

  Future<List<CalendarEvent>> getEventsForDate(DateTime date) async {
    final startOfDay = DateTime(date.year, date.month, date.day);
    final endOfDay = DateTime(date.year, date.month, date.day, 23, 59, 59);
    
    return await getEventsInRange(startOfDay, endOfDay);
  }

  Future<CalendarEvent?> createEvent(CalendarEvent event) async {
    try {
      final response = await _apiService.post(
        ApiConfig.calendarEvents,
        event.toJson(),
      );

      if (_apiService.isSuccess(response)) {
        final data = _apiService.parseResponse(response);
        return CalendarEvent.fromJson(data);
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      print('Error creating event: $e');
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
      print('Error updating event: $e');
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
      print('Error deleting event: $e');
      return false;
    }
  }


  Future<Map<String, dynamic>> generateSchedule(
    DateTime startDate,
    DateTime endDate,
  ) async {
    try {
      final response = await _apiService.post(
        ApiConfig.generateSchedule,
        {
          'startDate': startDate.toIso8601String().split('T')[0],
          'endDate': endDate.toIso8601String().split('T')[0],
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
      print('Error generating schedule: $e');
      return {
        'success': false,
        'error': 'Verbindungsfehler: $e',
      };
    }
  }
}