<?php declare(strict_types=1);

namespace App\Model;

final class Article
{
	public const STATE_DRAFT = 'draft';
	public const STATE_REVIEW = 'review';
	public const STATE_ARCHIVED = 'archived';

	public function __construct(
		private int $id,
		private string $title,
		private ?string $summary = null,
	) {
	}

	public function getId(): int
	{
		return $this->id;
	}

	public function getTitle(): string
	{
		return $this->title;
	}

	public function getSummary(): ?string
	{
		return $this->summary;
	}

	public function isPublished(): bool
	{
		return $this->summary !== null;
	}
}
