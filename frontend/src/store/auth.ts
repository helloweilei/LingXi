import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { UserVO } from '../api/types';

interface AuthState {
  token: string | null;
  user: UserVO | null;
  setAuth: (token: string, user: UserVO) => void;
  setUser: (user: UserVO) => void;
  logout: () => void;
}

export const useAuth = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setAuth: (token, user) => set({ token, user }),
      setUser: (user) => set({ user }),
      logout: () => set({ token: null, user: null }),
    }),
    { name: 'lingxi-auth' }
  )
);
