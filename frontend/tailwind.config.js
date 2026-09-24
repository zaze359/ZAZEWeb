/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // 门户（个人展示）：二次元 / Y2K 暗色霓虹
        portal: {
          bg: '#0e0b1a', // 深紫黑底
          surface: '#191436', // 卡片表面
          surface2: '#241c45', // 浮层/输入
          border: '#332a5e', // 描边
          text: '#ece9ff', // 主文字
          muted: '#a99fd6', // 次要文字
          neon: '#ff4ecd', // 品红霓虹（主强调）
          neon2: '#2ce8ff', // 青蓝霓虹
          neon3: '#9b5cff', // 紫霓虹
          warn: '#fde24a' // 霓虹黄
        },
        // 后台（管理）：现代实用浅色
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
      keyframes: {
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(12px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' }
        },
        float: {
          '0%,100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-6px)' }
        },
        glow: {
          '0%,100%': { boxShadow: '0 0 0 rgba(255,78,205,0)' },
          '50%': { boxShadow: '0 0 18px rgba(255,78,205,0.55)' }
        }
      },
      animation: {
        'fade-up': 'fade-up .5s ease-out both',
        float: 'float 3s ease-in-out infinite',
        glow: 'glow 2.4s ease-in-out infinite'
      }
    }
  },
  plugins: []
}
