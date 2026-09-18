import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, RAMPING_STAGES, DEFAULT_THRESHOLDS } from '../lib/config.js';
import { registerUser } from '../lib/auth.js';
import { createPost, createComment, toggleLike } from '../lib/posts.js';
import { buildSummary } from '../lib/report.js';

const VIEWER_POOL_SIZE = 5;
const POST_COUNT = 40;

// シナリオ: 全体タイムライン取得(GET /api/posts)
// 新着順・コメント数・いいね数を含む一覧取得の負荷特性を確認する。
export const options = {
	stages: RAMPING_STAGES,
	thresholds: DEFAULT_THRESHOLDS,
};

export function setup() {
	// 投稿を書く専用ユーザーで、タイムラインにある程度のデータ量を作っておく。
	const author = registerUser('tl_author');
	const postIds = [];
	for (let i = 0; i < POST_COUNT; i++) {
		const post = createPost(author.accessToken, `k6 load test post #${i} (${author.username})`);
		postIds.push(post.id);
	}

	// 一部の投稿にコメント・いいねを付け、コメント数・いいね数の集計に意味を持たせる。
	const commenter = registerUser('tl_commenter');
	postIds.slice(0, 10).forEach((postId) => {
		createComment(commenter.accessToken, postId, 'nice post! (k6 load test)');
		toggleLike(commenter.accessToken, postId);
	});

	// 複数の閲覧用ユーザーを用意し、各VUがプールから使い回す(実際の複数ユーザーの
	// 同時アクセスに近い状況を再現するため、全VUで1アカウントを使い回さない)。
	const viewers = [];
	for (let i = 0; i < VIEWER_POOL_SIZE; i++) {
		const viewer = registerUser(`tl_viewer${i}`);
		viewers.push(viewer.accessToken);
	}

	return { viewers };
}

export default function (data) {
	const accessToken = data.viewers[(__VU - 1) % data.viewers.length];
	const res = http.get(`${BASE_URL}/api/posts`, {
		headers: { Authorization: `Bearer ${accessToken}` },
		tags: { name: 'GET /api/posts' },
	});
	check(res, {
		'timeline fetch succeeded (200)': (r) => r.status === 200,
		'response has content array': (r) => Array.isArray(r.json('content')),
	});
	sleep(1);
}

export function handleSummary(data) {
	return buildSummary(data, 'report-timeline.html', 'k6 Report: Timeline (GET /api/posts)');
}
