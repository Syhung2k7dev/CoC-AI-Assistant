# CoC AI Assistant

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)]()
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)]()
[![Build Status](https://github.com/Syhung2k7dev/CoC-AI-Assistant/actions/workflows/android-build.yml/badge.svg)](https://github.com/Syhung2k7dev/CoC-AI-Assistant/actions)

**CoC AI Assistant** là giải pháp tự động hóa thông minh cho Clash of Clans, sử dụng mô hình ngôn ngữ lớn (LLM) và Vision-based Action Engine để thực hiện các thao tác tự động hóa với độ chính xác cao và khả năng thích nghi tốt.

## 🌟 Tính năng chính

- **Unified Action Registry:** Hệ thống lưu trữ hành động tập trung, kết hợp giữa tự học (AI) và tinh chỉnh thủ công (User).
- **Vision-Anchored Action:** Click dựa trên "Mỏ neo thị giác" (Anchor) thay vì tọa độ cứng, đảm bảo độ chính xác trên mọi độ phân giải.
- **Snapshot-based Overlay:** Công cụ tinh chỉnh tọa độ trực quan, tĩnh (không jitter), cho phép căn chỉnh Offset chính xác tuyệt đối.
- **Auto-Learning & Feedback:** Tự động ghi lại lịch sử thao tác, tối ưu hóa qua từng lần thực thi và cho phép người dùng can thiệp thủ công khi cần.
- **ActionExecutor thông minh:** Cơ chế thực thi "lì lợm" với khả năng fallback thông minh, đảm bảo bot không bị treo khi gặp sự cố visual.

## 🚀 Kiến trúc hệ thống mới

Hệ thống đã chuyển đổi sang mô hình **Unified Self-Learning Loop**:
1. **Vision Engine:** Sử dụng OpenCV template matching để định vị các UI element.
2. **Action Executor:** Thực hiện công thức `ClickPoint = AnchorPoint + Offset` để đảm bảo click chuẩn xác.
3. **Overlay & Feedback:** Giao diện điều chỉnh "Snapshot" cho phép người dùng hiệu chỉnh Offset trực tiếp.
4. **Action History:** Hệ thống tự động ghi vết và dọn dẹp lịch sử hành động (tối đa 20 mục gần nhất).

## 🛠 Yêu cầu kỹ thuật

- **Kotlin:** 2.0.21
- **Gradle:** 8.9 (Plugin 8.7.3)
- **Min SDK:** 23 (Android 6.0+)
- **Compile SDK:** 35
- **Dependencies:** Room, Jetpack Compose, OpenCV, Gemini SDK.

## 🧪 Kiểm thử & Phát triển

Dự án sử dụng cơ chế tự động hóa quy trình CI/CD qua GitHub Actions. Mọi thay đổi cấu trúc Database (Migration) đều được quản lý chặt chẽ.

> **Lưu ý:** Dự án hiện có hai Room database lịch sử với schema riêng. Không tự ý xóa database hoặc dùng destructive migration; mọi thay đổi schema phải đi kèm migration tương ứng và cần được kiểm tra trước khi phát hành.

---
*Dự án dành cho mục đích nghiên cứu và tự động hóa cá nhân.*

