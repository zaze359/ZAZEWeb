// // webpack.config.js
// const VueLoaderPlugin = require('vue-loader/lib/plugin')

// module.exports = {
//     entry: "./main.js",     // 入口js文件
//     output: {
//         path: __dirname,
//         filename: "build.js"    // 输出js文件
//     },
//     module: {
//         loaders: [
//             {
//                 test: /\.vue$/,
//                 loader: 'vue-loader'
//             },
//             // 它会应用到普通的 `.js` 文件
//             // 以及 `.vue` 文件中的 `<script>` 块
//             {
//                 test: /\.js$/,
//                 loader: 'babel-loader'
//             },
//             // 它会应用到普通的 `.css` 文件
//             // 以及 `.vue` 文件中的 `<style>` 块
//             {
//                 test: /\.css$/,
//                 use: [
//                     'vue-style-loader',
//                     'css-loader'
//                 ]
//             }
//         ]
//     },
//     resolve: {
//         alias: {
//             'vue$': 'vue/dist/vue.common.js'
//         }
//     },
//     plugins: [
//         // 请确保引入这个插件！
//         new VueLoaderPlugin()
//     ]
// }