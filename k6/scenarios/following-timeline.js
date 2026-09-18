import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, RAMPING_STAGES, DEFAULT_THRESHOLDS } from '../lib/config.js';
import { registerUser } from '../lib/auth.js';
import { createPost, createComment, toggleLike, followUser } from '../lib/posts.js';
import { buildSummary } from '../lib/report.js';

const FOLLOWER_POOL_SIZE = 5;
const FOLLOWEE_COUNT = 10;
const POSTS_PER_FOLLOWEE = 4;

// シナリオ: フォロー中タイムライン取得(GET /api/timeline/following)
// N+1対策として一括集計APIを実装した経緯があるため、実際に負荷をかけて
// レスポンスタイムを確認する。
export const options = {
	stages: RAMPING_STAGES,
	thresholds: DEFAULT_THRESHOLDS,
};

export function setup() {
	// フォロー対象(フォロイー)を複数人作り、それぞれ投稿・コメント・いいねを付ける。
	const followeeIds = [];
	for (let i = 0; i < FOLLOWEE_COUNT; i++) {
		const followee = registerUser(`fol_followee${i}`);
		followeeIds.push(followee.userId);

		for (let p = 0; p < POSTS_PER_FOLLOWEE; p++) {
			const post = createPost(followee.accessToken, `k6 load test post from followee ${i}-${p}`);
			if (p % 2 === 0) {
				createComment(followee.accessToken, post.id, 'nice post! (k6 load test)');
				toggleLike(followee.accessToken, post.id);
			}
		}
	}

	// フォロワー役のユーザーを複数作り、全員が全フォロイーをフォローする。
	const followers = [];
	for (let i = 0; i < FOLLOWER_POOL_SIZE; i++) {
		const follower = registerUser(`fol_follower${i}`);
		followeeIds.forEach((followeeId) => followUser(follower.accessToken, followeeId));
		followers.push(follower.accessToken);
	}

	return { followers };
}

export default function (data) {
	const accessToken = data.followers[(__VU - 1) % data.followers.length];
	const res = http.get(`${BASE_URL}/api/timeline/following`, {
		headers: { Authorization: `Bearer ${accessToken}` },
		tags: { name: 'GET /api/timeline/following' },
	});
	check(res, {
		'following timeline fetch succeeded (200)': (r) => r.status === 200,
		'response has content array': (r) => Array.isArray(r.json('content')),
	});
	sleep(1);
}

export function handleSummary(data) {
	return buildSummary(data, 'report-following-timeline.html', 'k6 Report: Following Timeline (GET /api/timeline/following)');
}
