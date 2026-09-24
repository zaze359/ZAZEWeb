/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // 门户（个人展示）：清爽冷调暗色
        // 设计基线 —— 中性灰黑底 + 薄荷绿唯一主色 + 琥珀黄点缀 + 天蓝辅助；
        // 靠「亮度分层 + 低对比描边」建立层次，不靠霓虹/光晕堆色彩（参考 aigodlike.com 的克制感）。
        portal: {
          bg: '#16171b', // 页面底：中性冷灰黑（非紫调）
          bg2: '#1b1c22', // 次级底：导航 / 分区带
          surface: '#22232a', // 卡片
          surface2: '#2b2c34', // 浮层 / 输入 / chip
          border: '#32333c', // 基础描边（低对比）
          border2: '#464757', // hover / 强调描边
          text: '#f0f1f5', // 主文字
          muted: '#969aa6', // 次要文字
          mint: '#34d8a0', // 主强调：薄荷绿
          mint2: '#26b585', // 主强调 hover / 深一档
          amber: '#ffd24d', // 次强调：琥珀黄（点亮态 / 高等级）
          sky: '#4c8df6', // 辅助：天蓝
          coral: '#f2555a' // 语义：警示 / 错误
        },
        // 后台（管理）：现代实用浅色 —— 保持克制的实用风，不参与门户风格调整
        admin: {
          bg: '#f7f8fa',
          surface: '#ffffff',
          border: '#e5e7eb',
          text: '#1f2937',
          muted: '#6b7280',
          accent: '#4f46e5'
        }
      },
      fontFamily: {
        sans: ['"PingFang SC"', '"Microsoft YaHei"', 'system-ui', 'sans-serif'],
        display: ['"PingFang SC"', 'system-ui', 'sans-serif']
      },
      borderRadius: {
        xl2: '1rem',
        xl3: '1.5rem'
      },
      boxShadow: {
        // 柔和暗投影，替代原先的霓虹光晕
        tile: '0 1px 2px rgba(0,0,0,.28), 0 8px 24px -12px rgba(0,0,0,.55)',
        mint: '0 8px 22px -10px rgba(52,216,160,.45)'
      },
      keyframes: {
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(12px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' }
        },
        float: {
          '0%,100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-6px)' }
        }
      },
      animation: {
        'fade-up': 'fade-up .5s ease-out both',
        float: 'float 3s ease-in-out infinite'
      }
    }
  },
  plugins: []
}
