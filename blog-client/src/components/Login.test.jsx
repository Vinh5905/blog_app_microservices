import React from 'react';
import { beforeEach, expect, test, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import Login from './Login';

const { login } = vi.hoisted(() => ({ login: vi.fn() }));
vi.mock('../hooks/useAuth', () => ({ useAuth: () => ({ login }) }));

beforeEach(() => { login.mockReset(); });

function submitLogin() {
  render(<MemoryRouter initialEntries={['/login']}>
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<div>Authenticated home</div>} />
    </Routes>
  </MemoryRouter>);
  fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'alice' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'example-password' } });
  fireEvent.click(screen.getByRole('button', { name: 'Login' }));
}

test('successful login preserves credentials and navigates home', async () => {
  login.mockResolvedValue({ token: 'test-token' });
  submitLogin();
  expect(await screen.findByText('Authenticated home')).toBeInTheDocument();
  expect(login).toHaveBeenCalledWith('alice', 'example-password');
});

test('rejected login stays on the form and displays an error', async () => {
  login.mockRejectedValue(new Error('Unauthorized'));
  submitLogin();
  expect(await screen.findByText('Login failed. Please check your credentials.')).toBeInTheDocument();
  expect(screen.queryByText('Authenticated home')).not.toBeInTheDocument();
});
