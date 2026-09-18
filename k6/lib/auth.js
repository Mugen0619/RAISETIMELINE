import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './config.js';

// 負荷試験実行のたびに一意なユーザーを作れるよう、実行時刻を接頭辞に使う。
// usernameの上限が32文字(RegisterRequest参照)と短いため、base36で圧縮する。
export const RUN_ID = Date.now().toString(36);

export function registerUser(suffix, password = 'LoadTest123') {
	// usernameは英数字とアンダースコアのみ許可(^[a-zA-Z0-9_]+$)、最大32文字のため、
	// 呼び出し元が可読性のためにハイフン区切りで渡してきても正規化する。
	const username = `k6_${RUN_ID}_${suffix}`.replace(/-/g, '_');
	if (username.length > 32) {
		throw new Error(`generated username exceeds 32 chars: "${username}" (${username.length})`);
	}
	const res = http.post(
		`${BASE_URL}/api/auth/register`,
		JSON.stringify({
			username,
			email: `${username}@example.com`,
			password,
			displayName: username,
		}),
		{ headers: { 'Content-Type': 'application/json' } },
	);

	check(res, { 'register succeeded (201)': (r) => r.status === 201 });
	if (res.status !== 201) {
		throw new Error(`user registration failed for ${username}: ${res.status} ${res.body}`);
	}

	const body = res.json();
	return {
		userId: body.userId,
		username,
		email: `${username}@example.com`,
		password,
		accessToken: body.accessToken,
	};
}

export function login(email, password) {
	const res = http.post(
		`${BASE_URL}/api/auth/login`,
		JSON.stringify({ email, password }),
		{ headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /api/auth/login' } },
	);
	return res;
}

export function authHeaders(accessToken) {
	return { headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' } };
}
