import { check, sleep } from 'k6';
import { RAMPING_STAGES, DEFAULT_THRESHOLDS } from '../lib/config.js';
import { registerUser, login } from '../lib/auth.js';
import { buildSummary } from '../lib/report.js';

// シナリオ: ログイン(POST /api/auth/login)
// 仮想ユーザー数を10→30→50人と段階的に増やし、同一アカウントへの並行ログインの
// レスポンスタイムを計測する。
export const options = {
	stages: RAMPING_STAGES,
	thresholds: DEFAULT_THRESHOLDS,
};

export function setup() {
	const user = registerUser('login');
	return { email: user.email, password: user.password };
}

export default function (data) {
	const res = login(data.email, data.password);
	check(res, {
		'login succeeded (200)': (r) => r.status === 200,
		'response has accessToken': (r) => !!r.json('accessToken'),
	});
	sleep(1);
}

export function handleSummary(data) {
	return buildSummary(data, 'report-login.html', 'k6 Report: Login');
}
