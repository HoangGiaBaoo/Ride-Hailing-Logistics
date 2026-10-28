-- Thu hoi access token truoc han: token mang "tv" nho hon users.token_version bi tu choi o gateway/service
ALTER TABLE users ADD COLUMN token_version bigint NOT NULL DEFAULT 0;

-- Chuoi xoay vong refresh token: token cu tro toi token thay the no
ALTER TABLE refresh_tokens ADD COLUMN replaced_by uuid;
