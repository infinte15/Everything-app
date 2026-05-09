import 'package:flutter/foundation.dart';
import 'package:intl/intl.dart';
import '../config/api_config.dart';
import '../models/habit.dart';
import 'api_service.dart';

class HabitService {
  final ApiService _apiService = ApiService();

  Future<List<Habit>> getAllHabits() async {
    try {
      final response = await _apiService.get('/api/habits');

      if (_apiService.isSuccess(response)) {
        final List<dynamic> data = _apiService.parseResponse(response);
        return data.map((json) => Habit.fromJson(json)).toList();
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error fetching habits: $e');
      return [];
    }
  }

  Future<Habit?> createHabit(Habit habit) async {
    try {
      final response = await _apiService.post(
        '/api/habits',
        habit.toJson(),
      );

      if (_apiService.isSuccess(response)) {
        final data = _apiService.parseResponse(response);
        return Habit.fromJson(data);
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error creating habit: $e');
      return null;
    }
  }

  Future<bool> completeHabit(int id, {DateTime? date}) async {
    try {
      final dateStr = date != null ? DateFormat('yyyy-MM-dd').format(date) : null;
      final url = '/api/habits/$id/complete${dateStr != null ? '?date=$dateStr' : ''}';
      
      final response = await _apiService.post(url, {});
      return _apiService.isSuccess(response);
    } catch (e) {
      debugPrint('Error completing habit: $e');
      return false;
    }
  }
}
