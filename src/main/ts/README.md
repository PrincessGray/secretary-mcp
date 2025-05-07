# 秘书管理系统 (TypeScript 版本)

这是秘书管理系统的TypeScript重构版本，使用现代前端技术栈构建。

## 技术栈

- **React 18**：用于构建用户界面
- **TypeScript**：提供类型安全
- **Vite**：快速的开发环境和构建工具
- **React Router**：客户端路由
- **Radix UI + Tailwind CSS**：UI组件和样式
- **Lucide React**：图标库

## 项目结构

```
src/
├── components/      # 组件
│   ├── ui/          # 基础UI组件
│   └── features/    # 功能组件
├── lib/             # 工具库
│   ├── hooks/       # React钩子
│   └── context/     # React上下文
├── services/        # API服务
├── types/           # TypeScript类型定义
├── utils/           # 实用工具函数
├── App.tsx          # 应用根组件
└── index.tsx        # 入口文件
```

## 开始使用

1. 安装依赖：
   ```
   npm install
   ```

2. 启动开发服务器：
   ```
   npm run dev
   ```

3. 构建生产版本：
   ```
   npm run build
   ```

## 与后端集成

项目配置了代理，可以在开发过程中直接访问后端API：

```
// vite.config.ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    }
  }
}
```

## 主题支持

系统支持亮色和暗色主题，通过ThemeProvider实现：

```jsx
<ThemeProvider defaultTheme="light" storageKey="secretary-theme">
  <App />
</ThemeProvider>
```

## 组件库

UI组件基于Radix UI原语构建，包括：

- Button：按钮
- Toast：通知提示
- Dialog：对话框
- 等更多组件

## 开发规范

- 使用函数式组件和React钩子
- 确保添加适当的TypeScript类型
- 遵循项目的文件夹结构
- 使用TailwindCSS进行样式设计

## 注意事项

这是一个持续开发中的项目，某些功能可能尚未完全实现。请参考代码注释和TODO标记。 