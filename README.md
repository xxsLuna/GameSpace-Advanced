# GameSpace Burn-In Guard

[crDroid GameSpace](https://github.com/crdroidandroid/android_packages_apps_GameSpace)
(브랜치 `14.0`, Android 14)를 단독 Gradle로 빌드 가능한 앱으로 옮기고, **OLED 번인 방지 /
방치형 절전 모드**를 추가한 Magisk·KernelSU 모듈입니다.

게임 세션 중 게임 바의 **화면 보호(Burn-in guard)** 타일, 또는 설정된 유휴 시간이 지나면
자동으로 다음 중 하나가 작동합니다.

| 모드 | 동작 | 해제 방법 |
|---|---|---|
| **모드 1 — Galaxy 스타일** | 불투명도 설정 가능한 검은 오버레이(80–100%)로 화면을 가리고, 중앙에 흐린 시계·배터리·잠금 링 표시. 화면 켜짐 유지, 1분마다 표시 위치 지터. 터치는 모두 차단 | 잠금 아이콘을 링 밖으로 드래그 |
| **모드 2 — Xiaomi 스타일** | 루트로 백라이트 노드에 `0`을 기록해 물리 소등(게임·GPU 파이프라인은 살아있음). 투명 터치 차단 오버레이 사용. 노드 쓰기 실패 시 모드 1로 자동 폴백 | 볼륨키 더블 누르기/길게 누르기, 또는 화면 5연속 탭 |

동작 중 추가 절전(설정에서 토글):

- **CPU/GPU 클럭 상한** — `scaling_max_freq` 및 GPU 클럭 노드를 설정 비율(기본 50%)로 제한, 해제 시 원복
- **주사율 60Hz 임시 고정** — `min/peak_refresh_rate`를 루트로 내려놓고 해제 시 원복
- **자동 복원 워치독** — 설정한 최대 잠금 시간(기본 10분) 초과 시 강제 복원. 백라이트가 꺼진 채 방치되지 않습니다

유휴 자동 진입은 `InputManager.monitorGestureInput`(서명 권한 `MONITOR_INPUT`)으로 실제 터치를
감지하며, 모듈이 없는 롬에서는 자동 진입만 비활성화됩니다(수동 타일은 동작).

## 빌드

```bash
# Windows(Git Bash) / Linux 공통. JDK 17+, Android SDK(platforms;android-35) 필요
bash build.sh
# → dist/GameSpace-BurnInGuard-14.0-bg1.zip
```

`app/platform.jks`(AOSP 공개 testkey)로 서명되므로 crDroid 등 testkey 롬에 시스템
오버레이로 설치됩니다. `versionCode 999999`로 ROM 번들보다 항상 우선 설치됩니다.

## 설치

Magisk/KernelSU 앱에서 zip을 플래시하면 설치 스크립트가 `pm path io.chaldeaprjkt.gamespace`로
기존 GameSpace의 실제 파티션(`/system_ext` · `/system` · `/product`)을 감지해 그 자리에
오버레이합니다. 재부팅 후 게임 공간 설정 → **번인 방지 및 방치형 절전**에서 모드를 선택하세요.

```bash
adb push dist/GameSpace-BurnInGuard-14.0-bg1.zip /data/local/tmp/
adb shell su -c "magisk --install-module /data/local/tmp/GameSpace-BurnInGuard-14.0-bg1.zip"
adb reboot
```

> **셀프 빌드/자체 서명 롬 주의**: ROM이 AOSP 공개 testkey가 아닌 자체 플랫폼 키로
> GameSpace를 서명했다면, 첫 부팅에서 PackageManager가 구 레코드와 서명 불일치를
> 감지해 레코드를 정리하면서 스캔을 한 번 거부합니다. 동봉된 `service.sh`가 부팅 직후
> `pm install -r`로 모듈 APK를 직접 설치해 이 과정을 자동 통과시킵니다. 수동으로는:
>
> ```bash
> APK=$(adb shell su -c "find /data/adb/modules/gamespace_burnin_guard -name GameSpace.apk" | tr -d '\r' | head -1)
> adb shell su -c "pm install -r $APK"
> ```
>
> (레코드가 정리된 뒤 재부팅하면 시스템 디렉터리 스캔도 정상 통과합니다.)

## 문제 복구

- **부트루프**: 안전 모드(부팅 중 볼륨 아래 유지)로 진입하거나, 복구모드/adb에서
  `su -c 'rm -rf /data/adb/modules/gamespace_burnin_guard'` 후 재부팅.
  모듈 디렉터리에 `disable` 빈 파일을 만들어 임시 비활성화도 가능합니다.
- **화면이 꺼진 채로 남음**(모듈 강제 종료 등 특수 상황): 백라이트 노드에 직접 쓰면 됩니다.

  ```bash
  adb shell su -c 'cat /sys/class/leds/lcd-backlight/max_brightness'   # 노드 위치·최대값 확인
  adb shell su -c 'echo 255 > /sys/class/leds/lcd-backlight/brightness'
  ```

- **언인스톨**: Magisk 앱에서 모듈 삭제(또는 모듈 디렉터리 제거) 후 재부팅하면 ROM 번들
  GameSpace가 그대로 돌아옵니다.

## 저장소 구조

```
app/                  GameSpace 앱 (crDroid 14.0 포크 + burnin 패키지)
stubs/                compileOnly 숨은 API 스텁 (런타임에는 실제 프레임워크 사용)
settingslib-compat/   SettingsLib/lineageos.preference 호환 쉼 (APK에 번들)
magisk-module/        module.prop, customize.sh, system_ext 스테이징
build.sh              릴리스 빌드 + zip 조립
.github/workflows/    태그(v*) push 시 CI 빌드·릴리스
```

## 라이선스 · 저작 표기

Apache-2.0 ([LICENSE](LICENSE)). 상용 저작 및 수정 내역은 [NOTICE](NOTICE) 참조.
