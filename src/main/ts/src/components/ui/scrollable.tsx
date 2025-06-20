import React, { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface ScrollableProps {
  children: ReactNode;
  className?: string;
  maxHeight?: string;
}

export function Scrollable({ children, className, maxHeight }: ScrollableProps) {
  return (
    <div 
      className={cn("scrollbar-reserved", className)}
      style={maxHeight ? { maxHeight, overflowY: "auto" } : {}}
    >
      {children}
    </div>
  );
}

// 创建一个帮助函数，可以在任何需要滚动条样式的地方使用
export function scrollableStyles() {
  return `
    /* 滚动条样式 */
    .scrollbar-reserved {
      overflow-y: auto;
    }
    
    .scrollbar-reserved::-webkit-scrollbar {
      width: 6px;
      height: 6px;
    }
    
    .scrollbar-reserved::-webkit-scrollbar-track {
      background: transparent;
    }
    
    .scrollbar-reserved::-webkit-scrollbar-thumb {
      background-color: rgba(155, 155, 155, 0.5);
      border-radius: 3px;
    }
    
    .scrollbar-reserved::-webkit-scrollbar-thumb:hover {
      background-color: rgba(155, 155, 155, 0.8);
    }
  `;
}
