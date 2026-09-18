import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './config.js';
import { authHeaders } from './auth.js';

export function createPost(accessToken, body) {
	const res = http.post(
		`${BASE_URL}/api/posts`,
		JSON.stringify({ body, imageUrls: null }),
		{ ...authHeaders(accessToken), tags: { name: 'POST /api/posts (setup)' } },
	);
	check(res, { 'create post succeeded (201)': (r) => r.status === 201 });
	if (res.status !== 201) {
		throw new Error(`post creation failed: ${res.status} ${res.body}`);
	}
	return res.json();
}

export function createComment(accessToken, postId, body) {
	const res = http.post(
		`${BASE_URL}/api/posts/${postId}/comments`,
		JSON.stringify({ body }),
		{ ...authHeaders(accessToken), tags: { name: 'POST /api/posts/:id/comments (setup)' } },
	);
	check(res, { 'create comment succeeded (201)': (r) => r.status === 201 });
	return res;
}

export function toggleLike(accessToken, postId) {
	const res = http.post(
		`${BASE_URL}/api/posts/${postId}/likes`,
		null,
		{ ...authHeaders(accessToken), tags: { name: 'POST /api/posts/:id/likes (setup)' } },
	);
	check(res, { 'toggle like succeeded (200)': (r) => r.status === 200 });
	return res;
}

export function followUser(accessToken, targetUserId) {
	const res = http.post(
		`${BASE_URL}/api/users/${targetUserId}/follow`,
		null,
		{ ...authHeaders(accessToken), tags: { name: 'POST /api/users/:id/follow (setup)' } },
	);
	check(res, { 'follow succeeded (200)': (r) => r.status === 200 });
	return res;
}
