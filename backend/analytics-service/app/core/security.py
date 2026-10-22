import hmac
from typing import Optional

from fastapi import Header, HTTPException, status

from app.core.config import settings


INTERNAL_TOKEN_HEADER = "X-Analytics-Internal-Token"


def require_internal_token(
    token: Optional[str] = Header(
        default=None,
        alias=INTERNAL_TOKEN_HEADER,
    ),
) -> None:
    if token is None or not hmac.compare_digest(
        token,
        settings.internal_token,
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid analytics service token",
        )