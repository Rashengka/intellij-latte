<?php declare(strict_types=1);

namespace App\Model;

final class ArticleFacade
{
	/** @return Article[] */
	public function findPublished(): array
	{
		return [];
	}

	public function getById(int $id): ?Article
	{
		return null;
	}
}
