-- 혼자 하기를 시작·종료 두 단계로. 시작 때 행을 만들고(score NULL) 종료 때 서버가 검증한 점수를 채운다
ALTER TABLE game_play ADD COLUMN token VARCHAR(32);
ALTER TABLE game_play ADD COLUMN seed BIGINT;
ALTER TABLE game_play ADD COLUMN finished_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE game_play ADD CONSTRAINT uk_game_play_token UNIQUE (token);
