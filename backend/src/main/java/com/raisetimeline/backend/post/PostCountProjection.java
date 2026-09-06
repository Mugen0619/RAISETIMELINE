package com.raisetimeline.backend.post;

/**
 * post_idごとの件数集計(コメント数・いいね数)を表す共通の集計結果。
 * CommentRepository/LikeRepositoryの一括カウントクエリの戻り値として使う。
 */
public interface PostCountProjection {

	Long getPostId();

	long getCount();
}
