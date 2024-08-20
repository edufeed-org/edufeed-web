/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,js,cljs}"],
  theme: {
    extend: {
      keyframes: {
        flyIn: {
          '0%': { transform: 'translateX(-100%)', opacity: '0' },
          '100%': { transform: 'translateX(0)', opacity: '1' },
        },
      },
      animation: {
        flyIn: 'flyIn 0.5s ease-out forwards',
      },

    },
  },
  plugins: [require("daisyui")],
}

