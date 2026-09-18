import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/3.0.4/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

// 各シナリオのhandleSummary()から呼び出す共通ヘルパー。
// ターミナルへのテキストサマリー出力と、指定ファイル名でのHTMLレポート出力を両方行う。
export function buildSummary(data, htmlFileName, title) {
	return {
		stdout: textSummary(data, { indent: ' ', enableColors: true }),
		[htmlFileName]: htmlReport(data, { title }),
	};
}
