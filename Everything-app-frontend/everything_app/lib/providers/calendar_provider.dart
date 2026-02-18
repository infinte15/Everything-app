import 'package:flutter/material.dart';
import '../models/calendar_event.dart';
import '../services/calendar_service.dart';

class CalendarProvider with ChangeNotifier {
  final CalendarService _calendarService = CalendarService();
  
  List<CalendarEvent> _events = [];
  bool _isLoading = false;
  String? _error;
  DateTime _focusedDay = DateTime.now();
  DateTime? _selectedDay;

  // Getters
  List<CalendarEvent> get events => _events;
  bool get isLoading => _isLoading;
  String? get error => _error;
  DateTime get focusedDay => _focusedDay;
  DateTime? get selectedDay => _selectedDay;

  List<CalendarEvent> getEventsForDay(DateTime day) {
    return _events.where((event) {
      return event.startTime.year == day.year &&
             event.startTime.month == day.month &&
             event.startTime.day == day.day;
    }).toList();
  }

  
  List<CalendarEvent> get selectedDayEvents {
    if (_selectedDay == null) return [];
    return getEventsForDay(_selectedDay!);
  }

  
  void setFocusedDay(DateTime day) {
    _focusedDay = day;
    notifyListeners();
  }

  
  void setSelectedDay(DateTime? day) {
    _selectedDay = day;
    notifyListeners();
  }

  
  Future<void> loadEventsForMonth(DateTime month) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      final firstDay = DateTime(month.year, month.month, 1);
      final lastDay = DateTime(month.year, month.month + 1, 0, 23, 59, 59);
      
      _events = await _calendarService.getEventsInRange(firstDay, lastDay);
      _error = null;
    } catch (e) {
      _error = 'Fehler beim Laden der Events: $e';
    }

    _isLoading = false;
    notifyListeners();
  }

  
  Future<void> loadEventsInRange(DateTime start, DateTime end) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      _events = await _calendarService.getEventsInRange(start, end);
      _error = null;
    } catch (e) {
      _error = 'Fehler beim Laden der Events: $e';
    }

    _isLoading = false;
    notifyListeners();
  }

  
  Future<bool> addEvent(CalendarEvent event) async {
    try {
      final created = await _calendarService.createEvent(event);
      
      if (created != null) {
        _events.add(created);
        notifyListeners();
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Erstellen des Events: $e';
      notifyListeners();
      return false;
    }
  }

  
  Future<bool> updateEvent(CalendarEvent event) async {
    try {
      final updated = await _calendarService.updateEvent(event);
      
      if (updated != null) {
        final index = _events.indexWhere((e) => e.id == event.id);
        if (index != -1) {
          _events[index] = updated;
          notifyListeners();
        }
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Aktualisieren des Events: $e';
      notifyListeners();
      return false;
    }
  }

  
  Future<bool> deleteEvent(int id) async {
    try {
      final success = await _calendarService.deleteEvent(id);
      
      if (success) {
        _events.removeWhere((e) => e.id == id);
        notifyListeners();
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Löschen des Events: $e';
      notifyListeners();
      return false;
    }
  }

 
  Future<Map<String, dynamic>> generateSchedule(
    DateTime startDate,
    DateTime endDate,
  ) async {
    _isLoading = true;
    notifyListeners();

    final result = await _calendarService.generateSchedule(startDate, endDate);

    if (result['success']) {
      // Reload events after schedule generation
      await loadEventsInRange(startDate, endDate);
    }

    _isLoading = false;
    notifyListeners();

    return result;
  }

  
  void clearError() {
    _error = null;
    notifyListeners();
  }
}