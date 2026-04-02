/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'geek-green': '#34c759', // Apple Green
        'deep-blue': '#1c1c1e',
        'tech-dark': '#F5F5F7', // 苹果浅灰背景
        'tech-card': '#ffffff',
        'apple-blue': '#0071E3', // 苹果蓝
        'apple-text': '#1D1D1F', // 深炭黑
        'apple-gray': '#86868B', // 正文灰
      },
      fontFamily: {
        sans: ['-apple-system', 'BlinkMacSystemFont', 'San Francisco', 'Inter', 'Helvetica Neue', 'sans-serif'],
      },
      transitionTimingFunction: {
        'spring': 'cubic-bezier(0.25, 0.1, 0.25, 1)',
      },
      animation: {
        'spin-slow': 'spin 15s linear infinite',
        'float': 'float 6s ease-in-out infinite',
      },
      keyframes: {
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-20px)' },
        }
      }
    },
  },
  plugins: [],
}