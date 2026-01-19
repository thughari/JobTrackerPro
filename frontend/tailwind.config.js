/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,ts}"],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        gray: {
          750: "#2d3748",
          850: "#1a202c",
          950: "#0B0E14",
          900: "#111827",
          800: "#151A23",
          700: "#1F2937",
        },
        primary: {
          500: "#6366f1",
          600: "#4f46e5",
        },
      },
    },
  },
  plugins: [],
};
