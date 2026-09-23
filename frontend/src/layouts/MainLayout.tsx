import { useEffect, useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { Avatar, Dropdown, Tag, message } from 'antd';
import { EditOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons';
import { useAuth } from '../store/auth';
import { authApi, aiApi } from '../api';
import { get } from '../api/http';
import type { UserVO } from '../api/types';

export default function MainLayout() {
  const navigate = useNavigate();
  const { user, token, setUser, logout } = useAuth();

  useEffect(() => {
    if (token && !user) {
      get<UserVO>('/auth/me')
        .then(setUser)
        .catch(() => undefined);
    }
  }, [token, user, setUser]);

  const onMenuClick = async (key: string) => {
    if (key === 'logout') {
      try {
        await authApi.me();
      } catch {
        /* ignore */
      }
      logout();
      message.success('已退出登录');
      navigate('/login');
    }
  };

  return (
    <div>
      <header className="app-header">
        <div className="app-logo" onClick={() => navigate('/')}>
          <div className="logo-icon brand-gradient">
            <EditOutlined />
          </div>
          <div>
            <div className="logo-title">灵犀创作</div>
            <div className="logo-sub">AI 小说创作平台</div>
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <AiStatusTag />
          <Dropdown
            menu={{
              items: [{ key: 'logout', icon: <LogoutOutlined />, label: '退出登录' }],
              onClick: ({ key }) => onMenuClick(key),
            }}
          >
            <span style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Avatar size={32} style={{ background: '#6a5acd' }} icon={<UserOutlined />} />
              <span style={{ color: '#4a4a5e' }}>{user?.nickname ?? user?.username ?? ''}</span>
            </span>
          </Dropdown>
        </div>
      </header>
      <Outlet />
    </div>
  );
}

function AiStatusTag() {
  const [status, setStatus] = useState<{ provider: string; model: string } | null>(null);
  useEffect(() => {
    aiApi
      .status()
      .then(setStatus)
      .catch(() => undefined);
  }, []);
  const isMock = !status || status.provider === 'mock';
  return (
    <Tag color={isMock ? 'gold' : 'green'} style={{ marginLeft: 0 }}>
      {isMock ? 'AI 示例引擎' : `AI：${status?.model ?? '已接入'}`}
    </Tag>
  );
}
