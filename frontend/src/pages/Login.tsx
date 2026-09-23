import { useState } from 'react';
import { Button, Form, Input, Tabs, message } from 'antd';
import { EditOutlined, ReadOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api';
import { useAuth } from '../store/auth';

export default function Login() {
  const navigate = useNavigate();
  const setAuth = useAuth((s) => s.setAuth);
  const [loading, setLoading] = useState(false);

  const onFinish = async (tab: string, values: any) => {
    setLoading(true);
    try {
      const resp =
        tab === 'login'
          ? await authApi.login(values.username, values.password)
          : await authApi.register(values.username, values.password, values.nickname ?? '');
      setAuth(resp.token, resp.user);
      message.success(tab === 'login' ? '欢迎回来！' : '注册成功，开始创作吧！');
      navigate('/', { replace: true });
    } catch (e: any) {
      message.error(e?.message ?? '操作失败');
    } finally {
      setLoading(false);
    }
  };

  const loginForm = (
    <Form
      layout="vertical"
      size="large"
      onFinish={(v) => onFinish('login', v)}
      autoComplete="off"
    >
      <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
        <Input placeholder="用户名" />
      </Form.Item>
      <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
        <Input.Password placeholder="密码" />
      </Form.Item>
      <Button type="primary" htmlType="submit" block loading={loading}>
        登录
      </Button>
    </Form>
  );

  const registerForm = (
    <Form
      layout="vertical"
      size="large"
      onFinish={(v) => onFinish('register', v)}
      autoComplete="off"
    >
      <Form.Item
        name="username"
        rules={[
          { required: true, message: '请输入用户名' },
          { min: 3, max: 30, message: '用户名长度需在 3-30 之间' },
        ]}
      >
        <Input placeholder="用户名" />
      </Form.Item>
      <Form.Item name="nickname">
        <Input placeholder="笔名（选填）" />
      </Form.Item>
      <Form.Item
        name="password"
        rules={[
          { required: true, message: '请输入密码' },
          { min: 6, max: 64, message: '密码长度需在 6-64 之间' },
        ]}
      >
        <Input.Password placeholder="密码（至少 6 位）" />
      </Form.Item>
      <Button type="primary" htmlType="submit" block loading={loading}>
        注册并登录
      </Button>
    </Form>
  );

  return (
    <div className="login-page">
      <div className="login-brand brand-gradient">
        <h1>灵犀创作</h1>
        <div className="slogan">让灵感，落笔成章。</div>
        <div className="feat">
          <ThunderboltOutlined style={{ marginTop: 4 }} />
          <span>AI 续写 / 润色 / 扩写：卡文的时候，让 AI 接力，输出全程流式可见。</span>
        </div>
        <div className="feat">
          <ReadOutlined style={{ marginTop: 4 }} />
          <span>全书大纲一键生成：书名与简介就位后，AI 为你铺好整本书的骨架。</span>
        </div>
        <div className="feat">
          <EditOutlined style={{ marginTop: 4 }} />
          <span>角色与世界观数据库：设定一次沉淀，AI 每次生成都“读懂”你的书。</span>
        </div>
      </div>
      <div className="login-form-pane">
        <div className="login-card">
          <h2 style={{ textAlign: 'center', marginBottom: 24 }}>开始你的故事</h2>
          <Tabs
            centered
            items={[
              { key: 'login', label: '登录', children: loginForm },
              { key: 'register', label: '注册', children: registerForm },
            ]}
          />
        </div>
      </div>
    </div>
  );
}
