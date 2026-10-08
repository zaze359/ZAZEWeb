// 权限中文名 / 危险等级 / 分组词典。
// 覆盖 Android 核心权限约 80 条；未收录的权限回退显示原始名并标记 documented=false（UI 标注「未收录」）。
// 离线可用，不联网查询（与 AppMarketCnDict 的同类决策一致）。

import type { ApkPermission, PermissionLevel } from './types'

interface PermissionInfo {
  cn: string
  level: PermissionLevel
  group?: string
}

const C = {
  CALENDAR: '日历',
  CAMERA: '相机',
  CONTACTS: '通讯录',
  LOCATION: '位置',
  MICROPHONE: '麦克风',
  PHONE: '电话',
  SENSORS: '传感器',
  SMS: '短信',
  STORAGE: '存储',
  ACTIVITY: '活动识别',
  NEARBY: '附近设备',
  BODY: '身体传感器',
  NETWORK: '网络',
  OTHER: '其它'
} as const

const PERMISSION_DICT: Record<string, PermissionInfo> = {
  // ---------- 危险权限 ----------
  'android.permission.READ_CALENDAR': { cn: '读取日历', level: 'dangerous', group: C.CALENDAR },
  'android.permission.WRITE_CALENDAR': { cn: '写入日历', level: 'dangerous', group: C.CALENDAR },
  'android.permission.CAMERA': { cn: '使用相机', level: 'dangerous', group: C.CAMERA },
  'android.permission.READ_CONTACTS': { cn: '读取通讯录', level: 'dangerous', group: C.CONTACTS },
  'android.permission.WRITE_CONTACTS': { cn: '写入通讯录', level: 'dangerous', group: C.CONTACTS },
  'android.permission.GET_ACCOUNTS': { cn: '访问账户列表', level: 'dangerous', group: C.CONTACTS },
  'android.permission.ACCESS_FINE_LOCATION': { cn: '精确位置', level: 'dangerous', group: C.LOCATION },
  'android.permission.ACCESS_COARSE_LOCATION': { cn: '大致位置', level: 'dangerous', group: C.LOCATION },
  'android.permission.ACCESS_BACKGROUND_LOCATION': { cn: '后台定位', level: 'dangerous', group: C.LOCATION },
  'android.permission.RECORD_AUDIO': { cn: '录音', level: 'dangerous', group: C.MICROPHONE },
  'android.permission.READ_PHONE_STATE': { cn: '读取电话状态', level: 'dangerous', group: C.PHONE },
  'android.permission.READ_PHONE_NUMBERS': { cn: '读取本机号码', level: 'dangerous', group: C.PHONE },
  'android.permission.CALL_PHONE': { cn: '直接拨打电话', level: 'dangerous', group: C.PHONE },
  'android.permission.ANSWER_PHONE_CALLS': { cn: '接听来电', level: 'dangerous', group: C.PHONE },
  'android.permission.ADD_VOICEMAIL': { cn: '添加语音信箱', level: 'dangerous', group: C.PHONE },
  'android.permission.USE_SIP': { cn: '使用 SIP 通话', level: 'dangerous', group: C.PHONE },
  'android.permission.ACCEPT_HANDOVER': { cn: '接管来电', level: 'dangerous', group: C.PHONE },
  'android.permission.BODY_SENSORS': { cn: '身体传感器', level: 'dangerous', group: C.BODY },
  'android.permission.BODY_SENSORS_BACKGROUND': { cn: '后台身体传感器', level: 'dangerous', group: C.BODY },
  'android.permission.ACTIVITY_RECOGNITION': { cn: '活动识别', level: 'dangerous', group: C.ACTIVITY },
  'android.permission.SEND_SMS': { cn: '发送短信', level: 'dangerous', group: C.SMS },
  'android.permission.RECEIVE_SMS': { cn: '接收短信', level: 'dangerous', group: C.SMS },
  'android.permission.READ_SMS': { cn: '读取短信', level: 'dangerous', group: C.SMS },
  'android.permission.RECEIVE_WAP_PUSH': { cn: '接收 WAP 推送', level: 'dangerous', group: C.SMS },
  'android.permission.RECEIVE_MMS': { cn: '接收彩信', level: 'dangerous', group: C.SMS },
  'android.permission.READ_EXTERNAL_STORAGE': { cn: '读取存储', level: 'dangerous', group: C.STORAGE },
  'android.permission.WRITE_EXTERNAL_STORAGE': { cn: '写入存储', level: 'dangerous', group: C.STORAGE },
  'android.permission.MANAGE_EXTERNAL_STORAGE': { cn: '管理所有文件', level: 'dangerous', group: C.STORAGE },
  'android.permission.READ_MEDIA_IMAGES': { cn: '读取图片', level: 'dangerous', group: C.STORAGE },
  'android.permission.READ_MEDIA_VIDEO': { cn: '读取视频', level: 'dangerous', group: C.STORAGE },
  'android.permission.READ_MEDIA_AUDIO': { cn: '读取音频', level: 'dangerous', group: C.STORAGE },
  'android.permission.NEARBY_WIFI_DEVICES': { cn: '附近 Wi-Fi 设备', level: 'dangerous', group: C.NEARBY },
  'android.permission.BLUETOOTH_SCAN': { cn: '蓝牙扫描', level: 'dangerous', group: C.NEARBY },
  'android.permission.BLUETOOTH_CONNECT': { cn: '连接蓝牙设备', level: 'dangerous', group: C.NEARBY },
  'android.permission.BLUETOOTH_ADVERTISE': { cn: '蓝牙广播', level: 'dangerous', group: C.NEARBY },

  // ---------- 普通权限 ----------
  'android.permission.INTERNET': { cn: '访问网络', level: 'normal', group: C.NETWORK },
  'android.permission.ACCESS_NETWORK_STATE': { cn: '查看网络状态', level: 'normal', group: C.NETWORK },
  'android.permission.ACCESS_WIFI_STATE': { cn: '查看 Wi-Fi 状态', level: 'normal', group: C.NETWORK },
  'android.permission.CHANGE_NETWORK_STATE': { cn: '更改网络状态', level: 'normal', group: C.NETWORK },
  'android.permission.CHANGE_WIFI_STATE': { cn: '更改 Wi-Fi 状态', level: 'normal', group: C.NETWORK },
  'android.permission.WAKE_LOCK': { cn: '唤醒锁', level: 'normal', group: C.OTHER },
  'android.permission.VIBRATE': { cn: '振动', level: 'normal', group: C.OTHER },
  'android.permission.FOREGROUND_SERVICE': { cn: '前台服务', level: 'normal', group: C.OTHER },
  'android.permission.POST_NOTIFICATIONS': { cn: '发送通知', level: 'normal', group: C.OTHER },
  'android.permission.RECEIVE_BOOT_COMPLETED': { cn: '开机自启', level: 'normal', group: C.OTHER },
  'android.permission.REQUEST_INSTALL_PACKAGES': { cn: '安装应用', level: 'normal', group: C.OTHER },
  'android.permission.REQUEST_DELETE_PACKAGES': { cn: '卸载应用', level: 'normal', group: C.OTHER },
  'android.permission.READ_SYNC_SETTINGS': { cn: '读取同步设置', level: 'normal', group: C.OTHER },
  'android.permission.WRITE_SYNC_SETTINGS': { cn: '写入同步设置', level: 'normal', group: C.OTHER },
  'android.permission.GET_PACKAGE_SIZE': { cn: '查询占用空间', level: 'normal', group: C.OTHER },
  'android.permission.REORDER_TASKS': { cn: '重排任务', level: 'normal', group: C.OTHER },
  'android.permission.EXPAND_STATUS_BAR': { cn: '展开通知栏', level: 'normal', group: C.OTHER },
  'android.permission.NFC': { cn: '使用 NFC', level: 'normal', group: C.OTHER },
  'android.permission.FLASHLIGHT': { cn: '使用闪光灯', level: 'normal', group: C.OTHER },
  'android.permission.MODIFY_AUDIO_SETTINGS': { cn: '修改音频设置', level: 'normal', group: C.OTHER },
  'android.permission.KILL_BACKGROUND_PROCESSES': { cn: '结束后台进程', level: 'normal', group: C.OTHER },
  'android.permission.SCHEDULE_EXACT_ALARM': { cn: '设定精确闹钟', level: 'normal', group: C.OTHER },
  'android.permission.USE_BIOMETRIC': { cn: '使用生物识别', level: 'normal', group: C.OTHER },
  'android.permission.USE_FINGERPRINT': { cn: '使用指纹', level: 'normal', group: C.OTHER },
  'android.permission.AUTHENTICATE_ACCOUNTS': { cn: '验证账户', level: 'normal', group: C.OTHER },
  'android.permission.SET_ALARM': { cn: '设置闹钟', level: 'normal', group: C.OTHER },
  'android.permission.BROADCAST_STICKY': { cn: '发送粘性广播', level: 'normal', group: C.OTHER },
  'android.permission.TRANSMIT_IR': { cn: '使用红外', level: 'normal', group: C.OTHER },
  'com.android.launcher.permission.INSTALL_SHORTCUT': { cn: '创建桌面快捷方式', level: 'normal', group: C.OTHER },
  'com.android.launcher.permission.UNINSTALL_SHORTCUT': { cn: '移除桌面快捷方式', level: 'normal', group: C.OTHER },

  // ---------- 签名级权限（危险子集） ----------
  'android.permission.REQUEST_SIGNATURE_PERMISSION': { cn: '签名级权限', level: 'signature', group: C.OTHER },
  'android.permission.BIND_ACCESSIBILITY_SERVICE': { cn: '绑定无障碍服务', level: 'signature', group: C.OTHER },
  'android.permission.BIND_DEVICE_ADMIN': { cn: '绑定设备管理器', level: 'signature', group: C.OTHER },
  'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE': { cn: '绑定通知监听', level: 'signature', group: C.OTHER }
}

/** 短名：去掉 android.permission. 前缀，便于 UI 展示与排序 */
export function shortPermissionName(name: string): string {
  return name.replace(/^android\.permission\./, '')
}

/** 描述一条权限：命中词典用词典，未命中回退原始名 + level=unknown */
export function describePermission(
  name: string,
  extra: { custom?: boolean; sinceSdk23?: boolean; maxSdkVersion?: number } = {}
): ApkPermission {
  const info = PERMISSION_DICT[name]
  const isAndroidPermission = name.startsWith('android.permission.')
  return {
    name,
    cn: info ? info.cn : name,
    level: info ? info.level : isAndroidPermission ? 'unknown' : 'unknown',
    group: info?.group,
    documented: !!info,
    custom: extra.custom,
    sinceSdk23: extra.sinceSdk23,
    maxSdkVersion: extra.maxSdkVersion
  }
}
